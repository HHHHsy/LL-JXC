$env:DB_URL = 'jdbc:mysql://192.168.1.7:3306/jxc_db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&connectionCollation=utf8mb4_unicode_ci'
$env:DB_USERNAME = 'root'
$env:DB_PASSWORD = '123456'
Set-Location $PSScriptRoot
mvn spring-boot:run
