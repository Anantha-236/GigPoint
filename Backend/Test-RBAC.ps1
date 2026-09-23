param(
    [string]$ProjectUrl = "https://rkjqsxbcqvacjwwqjssh.supabase.co",
    [string]$PublishableKey = "sb_publishable_fuqrRp2UMgVWc5zI5kpNcA_4rRFMnfW"
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "DhwaniMitra RBAC verification"
Write-Host "Project: $ProjectUrl"
Write-Host ""

$email = Read-Host "Hosted Supabase account email"
$securePassword = Read-Host "Password" -AsSecureString
$password = [System.Net.NetworkCredential]::new("", $securePassword).Password

$session = Invoke-RestMethod `
    -Method Post `
    -Uri "$ProjectUrl/auth/v1/token?grant_type=password" `
    -Headers @{ apikey = $PublishableKey } `
    -ContentType "application/json" `
    -Body (@{ email = $email; password = $password } | ConvertTo-Json)

$token = $session.access_token
if ([string]::IsNullOrWhiteSpace($token)) {
    throw "Authentication succeeded without an access token."
}

Write-Host "Authenticated as: $($session.user.email)"
Write-Host "User ID: $($session.user.id)"
Write-Host ""

$context = Invoke-RestMethod `
    -Method Post `
    -Uri "$ProjectUrl/rest/v1/rpc/get_my_context" `
    -Headers @{ apikey = $PublishableKey; Authorization = "Bearer $token" } `
    -ContentType "application/json" `
    -Body "{}"

Write-Host "Merchant context:"
$context | ConvertTo-Json -Depth 20

if ($null -eq $context.shop -or [string]::IsNullOrWhiteSpace($context.shop.id)) {
    Write-Host ""
    Write-Host "No shop is assigned to this account."
    exit 0
}

$shopId = $context.shop.id

$access = Invoke-RestMethod `
    -Method Post `
    -Uri "$ProjectUrl/rest/v1/rpc/get_my_access" `
    -Headers @{ apikey = $PublishableKey; Authorization = "Bearer $token" } `
    -ContentType "application/json" `
    -Body (@{ p_shop_id = $shopId } | ConvertTo-Json)

Write-Host ""
Write-Host "RBAC access:"
$access | ConvertTo-Json -Depth 20

Write-Host ""
Write-Host "Selected checks:"
foreach ($permission in @(
    "product.read",
    "product.create",
    "inventory.read",
    "inventory.adjust",
    "sale.create",
    "profit.read",
    "member.invite",
    "business.delete"
)) {
    $allowed = $access.is_owner -or ($access.permissions -contains $permission)
    Write-Host ("{0,-28} {1}" -f $permission, $(if ($allowed) { "ALLOW" } else { "DENY" }))
}
