#ssl gen (windows use git bash) at the root location of the project
```
cd src/main/resources

mkdir certs

cd certs

openssl genrsa -out keypair.pem 2048

openssl rsa -in keypair.pem -pubout -out public.pem

openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt -in keypair.pem -out private.pem

rm keypair.pem
```