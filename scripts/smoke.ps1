# End-to-end smoke test for ShopStream.
#
# Everything goes through the API gateway; no business service is called
# directly. The script exits with code 1 if any check fails, so it can be used
# in CI or before a demo.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts/smoke.ps1
#   powershell -ExecutionPolicy Bypass -File scripts/smoke.ps1 -BaseUrl http://localhost:8080

param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$StartupTimeoutSeconds = 240,
    [int]$SagaTimeoutSeconds = 60
)

$ErrorActionPreference = "Stop"
$script:Failures = 0
$script:Checks = 0

function Write-Pass([string]$Name) {
    Write-Host ("PASS  " + $Name) -ForegroundColor Green
}

function Write-Fail([string]$Name, [string]$Message) {
    Write-Host ("FAIL  " + $Name + " -> " + $Message) -ForegroundColor Red
}

function Assert([string]$Name, [scriptblock]$Body) {
    $script:Checks++
    try {
        & $Body
        Write-Pass $Name
    } catch {
        $script:Failures++
        Write-Fail $Name $_.Exception.Message
    }
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body,
        [int]$ExpectStatus = 200
    )
    $uri = $BaseUrl + $Path
    $params = @{
        Method      = $Method
        Uri         = $uri
        ContentType = "application/json"
        TimeoutSec  = 30
    }
    if ($null -ne $Body) {
        $params.Body = ($Body | ConvertTo-Json -Depth 10)
    }

    try {
        $response = Invoke-WebRequest @params -UseBasicParsing
        $status = [int]$response.StatusCode
        $content = $response.Content
        $headers = $response.Headers
    } catch {
        $webResponse = $_.Exception.Response
        if ($null -eq $webResponse) {
            throw
        }
        $status = [int]$webResponse.StatusCode
        $reader = New-Object System.IO.StreamReader($webResponse.GetResponseStream())
        $content = $reader.ReadToEnd()
        $reader.Close()
        $headers = $webResponse.Headers
    }

    if ($status -ne $ExpectStatus) {
        throw "Expected HTTP $ExpectStatus but got $status. Body: $content"
    }

    $json = $null
    if (-not [string]::IsNullOrWhiteSpace($content)) {
        try { $json = $content | ConvertFrom-Json } catch { $json = $null }
    }

    return [pscustomobject]@{
        Status  = $status
        Content = $content
        Json    = $json
        Headers = $headers
    }
}

function Wait-ForGateway {
    Write-Host "Waiting for the gateway at $BaseUrl ..." -ForegroundColor Cyan
    $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Method Get -Uri ($BaseUrl + "/api/products") -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -eq 200) {
                Write-Host "Gateway is ready." -ForegroundColor Cyan
                return
            }
        } catch {
            Start-Sleep -Seconds 3
        }
    }
    throw "The gateway did not become ready within $StartupTimeoutSeconds seconds"
}

function Wait-ForOrderStatus {
    param([long]$OrderId, [string[]]$Expected, [int]$TimeoutSeconds = $SagaTimeoutSeconds)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $last = $null
    while ((Get-Date) -lt $deadline) {
        $response = Invoke-Api -Method "GET" -Path ("/api/orders/" + $OrderId)
        $last = $response.Json.status
        if ($Expected -contains $last) {
            return $response.Json
        }
        Start-Sleep -Milliseconds 500
    }
    throw "Order $OrderId stayed in status '$last', expected one of $($Expected -join ', ')"
}

function Wait-ForSearchHit {
    param([string]$Query, [int]$TimeoutSeconds = 20)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $response = Invoke-Api -Method "GET" -Path ("/api/search?text=" + [uri]::EscapeDataString($Query))
        if ($response.Json.totalElements -ge 1) {
            return $response.Json
        }
        Start-Sleep -Milliseconds 500
    }
    throw "Search for '$Query' returned no documents; is search-service consuming product-events?"
}

# ---------------------------------------------------------------------------

Wait-ForGateway

$runId = [DateTime]::UtcNow.ToString("HHmmss")
$cartUser = "smoke-" + $runId

$productA = $null
$productB = $null
$order = $null
$rejectedOrder = $null

Assert "Create product A" {
    $script:productA = (Invoke-Api -Method "POST" -Path "/api/products" -ExpectStatus 201 -Body @{
        name        = "Smoke Laptop $runId"
        description = "14 inch developer laptop created by the smoke test"
        category    = "electronics"
        price       = 1299.00
        stock       = 5
    }).Json
    if ($null -eq $script:productA.id) { throw "No id in the response" }
}

Assert "Create product B" {
    $script:productB = (Invoke-Api -Method "POST" -Path "/api/products" -ExpectStatus 201 -Body @{
        name        = "Smoke Keyboard $runId"
        description = "Mechanical keyboard created by the smoke test"
        category    = "accessories"
        price       = 89.00
        stock       = 10
    }).Json
    if ($null -eq $script:productB.id) { throw "No id in the response" }
}

Assert "List products contains the new product" {
    $list = (Invoke-Api -Method "GET" -Path "/api/products").Json
    $ids = @($list | ForEach-Object { $_.id })
    if ($ids -notcontains $script:productA.id) { throw "Product $($script:productA.id) is missing from the list" }
}

Assert "Product read is stable (cache miss then cache hit)" {
    $first = Invoke-Api -Method "GET" -Path ("/api/products/" + $script:productA.id)
    $second = Invoke-Api -Method "GET" -Path ("/api/products/" + $script:productA.id)
    if ($first.Json.id -ne $script:productA.id) { throw "First read returned the wrong product" }
    if ($second.Json.id -ne $script:productA.id) { throw "Second read returned the wrong product" }
}

Assert "Update product and evict the cache" {
    $updated = (Invoke-Api -Method "PUT" -Path ("/api/products/" + $script:productA.id) -Body @{
        name        = $script:productA.name
        description = $script:productA.description
        category    = $script:productA.category
        price       = 1249.00
        stock       = 5
    }).Json
    if ([decimal]$updated.price -ne 1249.00) { throw "Price was not updated" }
}

Assert "Unknown product returns 404" {
    Invoke-Api -Method "GET" -Path "/api/products/999999" -ExpectStatus 404 | Out-Null
}

Assert "Search index receives the product through Kafka" {
    $hits = Wait-ForSearchHit -Query ("Smoke Laptop " + $runId)
    if ($hits.totalElements -lt 1) { throw "No search hit" }
}

Assert "Search filters by category and price" {
    $result = (Invoke-Api -Method "GET" -Path "/api/search?category=electronics&minPrice=500&maxPrice=2000&sort=priceAsc").Json
    foreach ($hit in @($result.content)) {
        if ($hit.category -ne "electronics") { throw "Unexpected category $($hit.category)" }
    }
}

Assert "Add an item to the cart" {
    $cart = (Invoke-Api -Method "POST" -Path ("/api/carts/$cartUser/items") -Body @{
        productId = $script:productA.id
        quantity  = 2
    }).Json
    if ($cart.items.Count -ne 1) { throw "Cart should contain one line" }
    if ([decimal]$cart.totalPrice -le 0) { throw "Cart total was not calculated" }
}

Assert "Changing the quantity recalculates the total" {
    $cart = (Invoke-Api -Method "PUT" -Path ("/api/carts/$cartUser/items/" + $script:productA.id) -Body @{
        quantity = 3
    }).Json
    if ($cart.items[0].quantity -ne 3) { throw "Quantity was not updated" }
}

Assert "Setting the quantity to zero removes the line" {
    $cart = (Invoke-Api -Method "PUT" -Path ("/api/carts/$cartUser/items/" + $script:productA.id) -Body @{
        quantity = 0
    }).Json
    if ($cart.items.Count -ne 0) { throw "Cart should be empty" }
}

Assert "Checkout creates a PENDING order and clears the cart" {
    Invoke-Api -Method "POST" -Path ("/api/carts/$cartUser/items") -Body @{
        productId = $script:productA.id
        quantity  = 2
    } | Out-Null

    $script:order = (Invoke-Api -Method "POST" -Path ("/api/carts/$cartUser/checkout") -ExpectStatus 201).Json
    if ($script:order.status -ne "PENDING") { throw "New orders must be PENDING, got $($script:order.status)" }

    $cart = (Invoke-Api -Method "GET" -Path ("/api/carts/$cartUser")).Json
    if ($cart.items.Count -ne 0) { throw "Checkout did not clear the cart" }
}

Assert "Inventory Saga confirms the order" {
    $confirmed = Wait-ForOrderStatus -OrderId $script:order.id -Expected @("CONFIRMED")
    if ($confirmed.status -ne "CONFIRMED") { throw "Order was not confirmed" }
}

Assert "Insufficient stock rejects the order" {
    $script:rejectedOrder = (Invoke-Api -Method "POST" -Path "/api/orders" -ExpectStatus 201 -Body @{
        items = @(@{ productId = $script:productA.id; quantity = 999 })
    }).Json
    $rejected = Wait-ForOrderStatus -OrderId $script:rejectedOrder.id -Expected @("REJECTED")
    if ([string]::IsNullOrWhiteSpace($rejected.failureReason)) { throw "Rejection reason is missing" }
}

Assert "Notifications describe the whole flow" {
    $notifications = (Invoke-Api -Method "GET" -Path ("/api/notifications/" + $script:order.id)).Json
    $types = @($notifications | ForEach-Object { $_.eventType })
    foreach ($expected in @("OrderPlaced", "StockReserved", "OrderConfirmed")) {
        if ($types -notcontains $expected) { throw "Missing notification $expected (got $($types -join ', '))" }
    }
}

Assert "Notifications include the rejection" {
    $notifications = (Invoke-Api -Method "GET" -Path ("/api/notifications/" + $script:rejectedOrder.id)).Json
    $types = @($notifications | ForEach-Object { $_.eventType })
    if ($types -notcontains "StockRejected") { throw "Missing StockRejected notification" }
}

Assert "Delete removes the product from the cache and the search index" {
    Invoke-Api -Method "DELETE" -Path ("/api/products/" + $script:productB.id) -ExpectStatus 204 | Out-Null
    Invoke-Api -Method "GET" -Path ("/api/products/" + $script:productB.id) -ExpectStatus 404 | Out-Null

    $deadline = (Get-Date).AddSeconds(20)
    while ((Get-Date) -lt $deadline) {
        $result = (Invoke-Api -Method "GET" -Path ("/api/search?text=" + [uri]::EscapeDataString("Smoke Keyboard " + $runId))).Json
        if ($result.totalElements -eq 0) { return }
        Start-Sleep -Milliseconds 500
    }
    throw "Product is still present in the search index"
}

Write-Host ""
if ($script:Failures -eq 0) {
    Write-Host ("Smoke test passed: " + $script:Checks + " checks") -ForegroundColor Green
    exit 0
} else {
    Write-Host ("Smoke test failed: " + $script:Failures + " of " + $script:Checks + " checks") -ForegroundColor Red
    exit 1
}
