import re

with open("app/src/main/java/com/example/security/VaultKeyManager.kt", "r") as f:
    content = f.read()

# Make a backup
with open("app/src/main/java/com/example/security/VaultKeyManager.kt.bak", "w") as f:
    f.write(content)

