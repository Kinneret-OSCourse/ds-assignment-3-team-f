param(
    [string]$Password = $env:MULLIGAN_TLS_PASSWORD
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($Password)) {
    $Password = "mulligan_tls_pw"
}

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$out = Join-Path $root "infra\certs"
$privateOut = Join-Path $root "infra\private-ca"
New-Item -ItemType Directory -Force -Path $out | Out-Null
New-Item -ItemType Directory -Force -Path $privateOut | Out-Null

$requiredFiles = @(
    "ca.pem",
    "server-cert.pem",
    "server-key.pem",
    "truststore.p12",
    "client-customer.p12",
    "client-peo.p12",
    "client-mo.p12",
    "client-server.p12"
)
$hasAllRequired = $true
foreach ($file in $requiredFiles) {
    if (-not (Test-Path (Join-Path $out $file))) {
        $hasAllRequired = $false
        break
    }
}
if ($hasAllRequired) {
    Write-Host "TLS material already present in $out"
    exit 0
}
if ((Get-ChildItem -LiteralPath $out -Force | Measure-Object).Count -gt 0) {
    Write-Host "Incomplete TLS material found in $out; regenerating the generated certificate set."
    Get-ChildItem -LiteralPath $out -Force |
        Where-Object { $_.Name -match '^(ca|server|client-|truststore)' -or $_.Extension -in @('.csr', '.ext', '.srl') } |
        Remove-Item -Force
}

function Write-Pem {
    param(
        [string]$Path,
        [string]$Label,
        [byte[]]$Bytes
    )

    $body = [Convert]::ToBase64String($Bytes, [Base64FormattingOptions]::InsertLineBreaks)
    $pem = "-----BEGIN $Label-----`n$body`n-----END $Label-----`n"
    [IO.File]::WriteAllText($Path, $pem, [Text.UTF8Encoding]::new($false))
}

function Join-Bytes {
    param([byte[][]]$Parts)
    $length = 0
    foreach ($part in $Parts) { $length += $part.Length }
    $bytes = New-Object byte[] $length
    $offset = 0
    foreach ($part in $Parts) {
        [Array]::Copy($part, 0, $bytes, $offset, $part.Length)
        $offset += $part.Length
    }
    return $bytes
}

function Asn1-Length {
    param([int]$Length)
    if ($Length -lt 128) {
        return [byte[]]@($Length)
    }
    $tmp = New-Object System.Collections.Generic.List[byte]
    $n = $Length
    while ($n -gt 0) {
        $tmp.Insert(0, [byte]($n -band 0xff))
        $n = $n -shr 8
    }
    return [byte[]](@(0x80 -bor $tmp.Count) + $tmp.ToArray())
}

function Asn1-Integer {
    param([byte[]]$Value)
    $i = 0
    while ($i -lt ($Value.Length - 1) -and $Value[$i] -eq 0) { $i++ }
    $trimmed = $Value[$i..($Value.Length - 1)]
    if (($trimmed[0] -band 0x80) -ne 0) {
        $trimmed = [byte[]](@(0) + $trimmed)
    }
    return Join-Bytes @([byte[]]@(0x02), (Asn1-Length $trimmed.Length), $trimmed)
}

function Asn1-Sequence {
    param([byte[][]]$Parts)
    $body = Join-Bytes $Parts
    return Join-Bytes @([byte[]]@(0x30), (Asn1-Length $body.Length), $body)
}

function Export-RsaPrivateKeyPkcs1 {
    param([Security.Cryptography.RSA]$Key)
    $p = $Key.ExportParameters($true)
    return Asn1-Sequence @(
        (Asn1-Integer ([byte[]]@(0))),
        (Asn1-Integer $p.Modulus),
        (Asn1-Integer $p.Exponent),
        (Asn1-Integer $p.D),
        (Asn1-Integer $p.P),
        (Asn1-Integer $p.Q),
        (Asn1-Integer $p.DP),
        (Asn1-Integer $p.DQ),
        (Asn1-Integer $p.InverseQ)
    )
}

$caKey = [Security.Cryptography.RSA]::Create(4096)
$caReq = [Security.Cryptography.X509Certificates.CertificateRequest]::new(
    "CN=Mulligan Dev CA",
    $caKey,
    [Security.Cryptography.HashAlgorithmName]::SHA256,
    [Security.Cryptography.RSASignaturePadding]::Pkcs1
)
$caReq.CertificateExtensions.Add(
    [Security.Cryptography.X509Certificates.X509BasicConstraintsExtension]::new($true, $false, 0, $true)
)
$caReq.CertificateExtensions.Add(
    [Security.Cryptography.X509Certificates.X509KeyUsageExtension]::new(
        [Security.Cryptography.X509Certificates.X509KeyUsageFlags]::KeyCertSign -bor
        [Security.Cryptography.X509Certificates.X509KeyUsageFlags]::CrlSign,
        $true
    )
)
$caCert = $caReq.CreateSelfSigned([DateTimeOffset]::UtcNow.AddDays(-1), [DateTimeOffset]::UtcNow.AddYears(10))

$serverKey = [Security.Cryptography.RSA]::Create(4096)
$serverReq = [Security.Cryptography.X509Certificates.CertificateRequest]::new(
    "CN=mulligan-rabbit",
    $serverKey,
    [Security.Cryptography.HashAlgorithmName]::SHA256,
    [Security.Cryptography.RSASignaturePadding]::Pkcs1
)
$san = [Security.Cryptography.X509Certificates.SubjectAlternativeNameBuilder]::new()
@("rabbit-1", "rabbit-2", "rabbit-3", "haproxy", "localhost") | ForEach-Object {
    $san.AddDnsName($_)
}
$serverReq.CertificateExtensions.Add($san.Build())
$serverReq.CertificateExtensions.Add(
    [Security.Cryptography.X509Certificates.X509KeyUsageExtension]::new(
        [Security.Cryptography.X509Certificates.X509KeyUsageFlags]::DigitalSignature -bor
        [Security.Cryptography.X509Certificates.X509KeyUsageFlags]::KeyEncipherment,
        $true
    )
)
$eku = [Security.Cryptography.OidCollection]::new()
$eku.Add([Security.Cryptography.Oid]::new("1.3.6.1.5.5.7.3.1")) | Out-Null
$serverReq.CertificateExtensions.Add(
    [Security.Cryptography.X509Certificates.X509EnhancedKeyUsageExtension]::new($eku, $false)
)
$serial = New-Object byte[] 16
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($serial)
$rng.Dispose()
$serverCert = $serverReq.Create($caCert, [DateTimeOffset]::UtcNow.AddDays(-1), [DateTimeOffset]::UtcNow.AddDays(825), $serial)

Write-Pem (Join-Path $out "ca.pem") "CERTIFICATE" $caCert.Export([Security.Cryptography.X509Certificates.X509ContentType]::Cert)
Write-Pem (Join-Path $privateOut "ca-key.pem") "RSA PRIVATE KEY" (Export-RsaPrivateKeyPkcs1 $caKey)
Write-Pem (Join-Path $out "server-cert.pem") "CERTIFICATE" $serverCert.Export([Security.Cryptography.X509Certificates.X509ContentType]::Cert)
Write-Pem (Join-Path $out "server-key.pem") "RSA PRIVATE KEY" (Export-RsaPrivateKeyPkcs1 $serverKey)

$truststore = Join-Path $out "truststore.p12"
if (Test-Path $truststore) {
    Remove-Item -LiteralPath $truststore -Force
}
& keytool -importcert -alias mulligan-ca -file (Join-Path $out "ca.pem") `
    -keystore $truststore -storetype PKCS12 -storepass $Password -noprompt | Out-Null
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $truststore)) {
    throw "keytool failed to create truststore.p12"
}

# Per-service client certs (for optional mTLS). One PKCS12 keystore per service
# containing its client cert + private key, with the CA imported in the chain.
foreach ($svc in @("customer", "peo", "mo", "server")) {
    $clientKey = [Security.Cryptography.RSA]::Create(4096)
    $clientReq = [Security.Cryptography.X509Certificates.CertificateRequest]::new(
        "CN=mulligan_$svc",
        $clientKey,
        [Security.Cryptography.HashAlgorithmName]::SHA256,
        [Security.Cryptography.RSASignaturePadding]::Pkcs1
    )
    $clientReq.CertificateExtensions.Add(
        [Security.Cryptography.X509Certificates.X509KeyUsageExtension]::new(
            [Security.Cryptography.X509Certificates.X509KeyUsageFlags]::DigitalSignature -bor
            [Security.Cryptography.X509Certificates.X509KeyUsageFlags]::KeyEncipherment,
            $true
        )
    )
    $clientEku = [Security.Cryptography.OidCollection]::new()
    $clientEku.Add([Security.Cryptography.Oid]::new("1.3.6.1.5.5.7.3.2")) | Out-Null   # clientAuth
    $clientReq.CertificateExtensions.Add(
        [Security.Cryptography.X509Certificates.X509EnhancedKeyUsageExtension]::new($clientEku, $false)
    )

    $serial = New-Object byte[] 16
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($serial)
    $rng.Dispose()

    $clientCert = $clientReq.Create($caCert, [DateTimeOffset]::UtcNow.AddDays(-1), [DateTimeOffset]::UtcNow.AddDays(825), $serial)
    $clientCertWithKey = [Security.Cryptography.X509Certificates.RSACertificateExtensions]::CopyWithPrivateKey($clientCert, $clientKey)

    $p12Path = Join-Path $out "client-$svc.p12"
    [IO.File]::WriteAllBytes(
        $p12Path,
        $clientCertWithKey.Export([Security.Cryptography.X509Certificates.X509ContentType]::Pfx, $Password)
    )

    Write-Pem (Join-Path $out "client-$svc-cert.pem") "CERTIFICATE" $clientCert.Export([Security.Cryptography.X509Certificates.X509ContentType]::Cert)
    Write-Pem (Join-Path $out "client-$svc-key.pem") "RSA PRIVATE KEY" (Export-RsaPrivateKeyPkcs1 $clientKey)
}

foreach ($file in $requiredFiles) {
    if (-not (Test-Path (Join-Path $out $file))) {
        throw "TLS generation finished with missing required file: $file"
    }
}

Write-Host "Generated TLS material in $out"
Write-Host "  truststore password = $Password"
Write-Host ""
Write-Host "Activate AMQPS:"
Write-Host "  docker compose -f docker-compose.yml -f docker-compose.tls.yml up -d --build"
