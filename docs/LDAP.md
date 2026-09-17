# Đăng nhập OpenLDAP trước khi chạy HdfsFileLister

`HdfsFileLister.main` yêu cầu đăng nhập trước `list`, `upload`, `analyze`
và cả khi chạy không có tham số. `Main.main` và `IpLogAnalysisDriver.run`
cũng yêu cầu xác thực, kể cả chạy driver trực tiếp bằng main hoặc ToolRunner.
Lệnh `analyze` xác thực tại driver nên chỉ đăng nhập một lần.
LDAP lỗi thì không chạy tác vụ HDFS.
Endpoint: `ldap://171.226.11.39:389`, bắt buộc StartTLS.
DN user: `uid=<username>,ou=users,dc=hadoop,dc=local`.

Đây là kiểm tra đăng nhập trong ứng dụng, không bật xác thực cho cụm HDFS,
không đổi user Hadoop và không cấp quyền HDFS theo user LDAP.
Muốn bảo vệ truy cập HDFS phải cấu hình gateway/Kerberos và giới hạn đường truy cập backend.

## Chạy trên Windows bằng PowerShell

```powershell
scp ptit@171.226.11.39:~/openldap-tls-2026/ca.crt "$env:USERPROFILE\openldap-ca.crt"
$env:LDAP_CA_CERT = "$env:USERPROFILE\openldap-ca.crt"
$env:LDAP_USERNAME = "hadoop"

mvn package
java -jar target/ip-log-analysis-1.0.0.jar list
```

Nhập mật khẩu ở prompt `LDAP password:`.Nhập mật khẩu `Hadoop@123`. Mật khẩu không hiển thị trên terminal.
