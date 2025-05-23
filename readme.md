# ssl gen (windows use git bash) at the root location of the project
```
cd src/main/resources

mkdir certs

cd certs

openssl genrsa -out keypair.pem 2048

openssl rsa -in keypair.pem -pubout -out public.pem

openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt -in keypair.pem -out private.pem

rm keypair.pem
```

# ssl gen linux at project root location
```
# Create certs/ directory
mkdir -p src/main/resources/certs

# Generate private key
openssl genpkey -algorithm RSA -out src/main/resources/certs/private.pem -pkeyopt rsa_keygen_bits:2048

# Extract public key
openssl rsa -pubout -in src/main/resources/certs/private.pem -out src/main/resources/certs/public.pem
```