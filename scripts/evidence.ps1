# ShopStream - evidence collector
#
# Runs the commands behind the screenshots in docs/DESIGN-REPORT.md and prints
# their output in a compact form, so every appendix entry can be reproduced.
#
# Usage (from the repository root):
#   .\scripts\evidence.ps1 ps         # container status and published ports
#   .\scripts\evidence.ps1 events     # Kafka event timeline across the services
#   .\scripts\evidence.ps1 kafka      # KRaft cluster id and topic partitions
#   .\scripts\evidence.ps1 redis      # cart hash, TTL and product cache keys
#   .\scripts\evidence.ps1 search     # Elasticsearch mapping, documents, search API
#   .\scripts\evidence.ps1 cache      # cache miss, hit and eviction
#   .\scripts\evidence.ps1 db         # MySQL tables, stock reservations, orders
#   .\scripts\evidence.ps1 gateway    # X-Request-Id lines from the gateway
#   .\scripts\evidence.ps1 all        # everything, in the order above

param(
    [ValidateSet("ps", "events", "kafka", "redis", "search", "cache", "cart", "db", "gateway", "all")]
    [string]$Section = "all"
)

$ErrorActionPreference = "Continue"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# Docker Desktop installs the CLI outside PATH on this machine; fall back to it.
$docker = (Get-Command docker -ErrorAction SilentlyContinue).Source
if (-not $docker) {
    $docker = Join-Path $env:LOCALAPPDATA "Programs\DockerDesktop\resources\bin\docker.exe"
}
$compose = @("-f", "docker-compose.yml", "-f", "docker-compose.dev.yml")

function Invoke-Dc {
    param([string[]]$Arguments)
    & $docker compose @compose @Arguments
}

function Write-Head {
    param([string]$Text)
    Write-Host ""
    Write-Host "== $Text " -NoNewline
    Write-Host ("=" * [Math]::Max(0, 74 - $Text.Length))
}

function Show-Ps {
    Write-Head "containers (only api-gateway is reachable from outside)"
    Invoke-Dc @("ps", "--format", "table {{.Name}}\t{{.Status}}\t{{.Ports}}")
}

function Show-Events {
    Write-Head "Kafka events and cache misses in the service logs"

    $services = @("order-service", "product-service-1", "product-service-2", "notification-service")
    $pattern = "Published |Received |Reserved stock|Rejected order|moved to|NOTIFICATION \["
    $skip = "clientId|eureka|Node -1|sasl\.|window\.factor|Metadata"

    $rows = Invoke-Dc (@("logs", "--no-color") + $services) 2>&1 |
        Where-Object { $_ -match "\|" } |
        Where-Object { $_ -match $pattern -and $_ -notmatch $skip } |
        ForEach-Object {
            $parts = $_ -split " \| ", 2
            $container = $parts[0].Trim()
            $body = $parts[1]
            $time = ($body -split " ")[0]
            if ($body.Contains(" : ")) { $message = $body.Substring($body.LastIndexOf(" : ") + 3) } else { $message = $body }

            [pscustomobject]@{
                Time      = $time
                Container = $container
                Message   = $message.Trim()
            }
        } |
        Sort-Object Time

    $rows | Select-Object -Last 30 | ForEach-Object {
        $clock = if ($_.Time -match "T(\d\d:\d\d:\d\d\.\d{3})") { $Matches[1] } else { $_.Time }
        $name = $_.Container -replace "-1$", ""
        $text = $_.Message
        if ($text.Length -gt 92) { $text = $text.Substring(0, 89) + "..." }
        "{0,-12} {1,-22} {2}" -f $clock, $name, $text
    }
}

function Show-Kafka {
    Write-Head "KRaft cluster id (no ZooKeeper container is running)"
    Invoke-Dc @("exec", "-T", "kafka", "/opt/kafka/bin/kafka-cluster.sh", "cluster-id", "--bootstrap-server", "kafka:9092")

    Write-Head "topics declared with NewTopic / TopicBuilder"
    Invoke-Dc @("exec", "-T", "kafka", "/opt/kafka/bin/kafka-topics.sh", "--bootstrap-server", "kafka:9092", "--describe") 2>&1 |
        Select-String -Pattern "^Topic: (order|inventory|product)-events" |
        ForEach-Object {
            if ($_.Line -match "^(Topic: [a-z-]+).*PartitionCount: (\d+).*ReplicationFactor: (\d+)") {
                "{0,-22} PartitionCount: {1}   ReplicationFactor: {2}" -f $Matches[1], $Matches[2], $Matches[3]
            }
        }
}

function Show-Redis {
    Write-Head "cart hash for demo-user (one field per attribute)"
    Invoke-Dc @("exec", "-T", "redis", "redis-cli", "-a", "mypass", "--no-auth-warning", "HGETALL", "carts:demo-user")

    Write-Head "cart TTL in seconds, written by @TimeToLive"
    Invoke-Dc @("exec", "-T", "redis", "redis-cli", "-a", "mypass", "--no-auth-warning", "TTL", "carts:demo-user")

    Write-Head "product cache entries created by @Cacheable"
    Invoke-Dc @("exec", "-T", "redis", "redis-cli", "-a", "mypass", "--no-auth-warning", "KEYS", "products::*")
}

function Show-Search {
    Write-Head "index mapping created by @Document / @Field (ES:9200)"
    $mapping = (Invoke-RestMethod "http://localhost:9200/products/_mapping").products.mappings.properties
    foreach ($field in @("name", "description", "category", "price", "stock")) {
        $definition = $mapping.$field
        $types = @()
        if ($definition.type) { $types += $definition.type }
        if ($definition.fields.keyword) { $types += "keyword" }
        "{0,-12} {1}" -f $field, ($types -join ", ")
    }

    Write-Head "documents in the index, written by the consumer"
    "count = " + (Invoke-RestMethod "http://localhost:9200/products/_count").count
    Invoke-Dc (@("logs", "--no-color", "search-service")) 2>&1 |
        Select-String -Pattern "Indexed |Deleted " |
        Select-Object -Last 2 |
        ForEach-Object { ($_.Line -split " : ")[-1] }

    Write-Head "Elasticsearch query and the gateway search API"
    $raw = Invoke-RestMethod "http://localhost:9200/products/_search" -Method Post -ContentType "application/json" `
        -Body (@{ query = @{ match = @{ description = "laptop" } } } | ConvertTo-Json -Depth 5)
    "es _search description=laptop -> hits = $($raw.hits.total.value), first = $($raw.hits.hits[0]._source.name)"
    $api = Invoke-RestMethod "http://localhost:8080/api/search?text=laptop"
    "gateway /api/search?text=laptop -> totalElements = $($api.totalElements), $($api.content[0].name)"
}

function Show-Cache {
    Write-Head "drop the cached entry, then read the product twice"
    Invoke-Dc @("exec", "-T", "redis", "redis-cli", "-a", "mypass", "--no-auth-warning", "DEL", "products::1") | Out-Null
    $first = Invoke-RestMethod "http://localhost:8080/api/products/1"
    $second = Invoke-RestMethod "http://localhost:8080/api/products/1"
    "GET /api/products/1 #1 -> $($first.name), price $($first.price)"
    "GET /api/products/1 #2 -> $($second.name), price $($second.price)"

    Write-Head "cache miss logged once, the second read is served from Redis"
    Invoke-Dc (@("logs", "--no-color", "--since", "30s", "product-service-1", "product-service-2")) 2>&1 |
        Select-String -Pattern "Cache miss|Cache hit" |
        ForEach-Object { ($_.Line -split " : ")[-1] }
    Invoke-Dc @("exec", "-T", "redis", "redis-cli", "-a", "mypass", "--no-auth-warning", "KEYS", "products::*")

    Write-Head "@CacheEvict removes the entry when the product is updated"
    Invoke-RestMethod -Method Put -Uri "http://localhost:8080/api/products/1" -ContentType "application/json" `
        -Body (@{ name = $first.name; description = $first.description; category = $first.category; price = $first.price; stock = $first.stock } | ConvertTo-Json) | Out-Null
    $after = @(Invoke-Dc @("exec", "-T", "redis", "redis-cli", "-a", "mypass", "--no-auth-warning", "KEYS", "products::*") | Where-Object { $_ })
    $text = if ($after.Count -gt 0) { $after -join ", " } else { "(none - the entry was evicted)" }
    "PUT /api/products/1 done, keys now: $text"
}

function Show-Cart {
    $base = "http://localhost:8080/api/carts/demo-user"

    Write-Head "cart API: add two items, read the cart"
    $empty = Invoke-RestMethod -Method Delete -Uri $base
    "DELETE cart (start from an empty cart) -> items=$($empty.items.Count)"
    $add1 = Invoke-RestMethod -Method Post -Uri "$base/items" -ContentType "application/json" -Body (@{ productId = 1; quantity = 1 } | ConvertTo-Json)
    "POST items (product 1 x1) -> items=$($add1.items.Count), totalPrice=$($add1.totalPrice), timeToLive=$($add1.timeToLive)"
    $add2 = Invoke-RestMethod -Method Post -Uri "$base/items" -ContentType "application/json" -Body (@{ productId = 2; quantity = 2 } | ConvertTo-Json)
    "POST items (product 2 x2) -> items=$($add2.items.Count), totalPrice=$($add2.totalPrice), timeToLive=$($add2.timeToLive)"
    $cart = Invoke-RestMethod -Uri $base
    foreach ($item in $cart.items) { "  $($item.name)  x$($item.quantity)  lineTotal=$($item.lineTotal)" }

    Write-Head "quantity update, then remove one item"
    $upd = Invoke-RestMethod -Method Put -Uri "$base/items/2" -ContentType "application/json" -Body (@{ quantity = 3 } | ConvertTo-Json)
    "PUT items/2 quantity=3 -> totalPrice=$($upd.totalPrice)"
    $del = Invoke-RestMethod -Method Delete -Uri "$base/items/2"
    "DELETE items/2 -> items=$($del.items.Count), totalPrice=$($del.totalPrice)"

    Write-Head "checkout hands the cart to order-service and deletes the hash"
    $order = Invoke-RestMethod -Method Post -Uri "$base/checkout"
    "POST checkout -> order id=$($order.id) status=$($order.status) totalPrice=$($order.totalPrice)"
    $empty = Invoke-RestMethod -Uri $base
    "GET cart after checkout -> items=$($empty.items.Count), totalPrice=$($empty.totalPrice)"
    "redis EXISTS carts:demo-user = " + (Invoke-Dc @("exec", "-T", "redis", "redis-cli", "-a", "mypass", "--no-auth-warning", "EXISTS", "carts:demo-user"))
}

function Show-Db {
    Write-Head "productdb: tables and the idempotency table"
    Invoke-Dc @("exec", "-T", "product-mysql", "mysql", "-uproduct", "-pproduct-pw", "-e",
        "USE productdb; SHOW TABLES; SELECT order_id, status, reason FROM stock_reservations; SELECT id, name, stock FROM products;") 2>&1 |
        Where-Object { $_ -notmatch "Using a password|insecure" }

    Write-Head "orderdb: the other service owns a different database"
    Invoke-Dc @("exec", "-T", "order-mysql", "mysql", "-uorder", "-porder-pw", "-e",
        "USE orderdb; SHOW TABLES; SELECT id, status, failure_reason FROM orders;") 2>&1 |
        Where-Object { $_ -notmatch "Using a password|insecure" }
}

function Show-Gateway {
    Write-Head "one request id per request, logged by the gateway"
    Invoke-Dc @("logs", "--no-color", "api-gateway") 2>&1 |
        Select-String -Pattern "XRequestIdFilter" |
        Select-Object -Last 12 |
        ForEach-Object { ($_.Line -split " : ")[-1] }
}

switch ($Section) {
    "ps" { Show-Ps }
    "events" { Show-Events }
    "kafka" { Show-Kafka }
    "redis" { Show-Redis }
    "search" { Show-Search }
    "cache" { Show-Cache }
    "cart" { Show-Cart }
    "db" { Show-Db }
    "gateway" { Show-Gateway }
    "all" {
        Show-Ps
        Show-Events
        Show-Kafka
        Show-Redis
        Show-Search
        Show-Cache
        Show-Db
        Show-Gateway
    }
}

