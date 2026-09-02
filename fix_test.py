import re

with open("app/src/test/java/com/example/QuantumVaultSecurityHardeningTest.kt", "r") as f:
    content = f.read()

content = content.replace(
"""    @Test
    fun testP0_2_PasswordCryptoHelperAesGcmTamperProof() {""",
"""    @Test
    fun testP0_2_PasswordCryptoHelperAesGcmTamperProof() {
        com.example.security.VaultKeyManager.initializeVrkWithPin(context, "1234")
        com.example.security.VaultKeyManager.authorizeWithPin(context, "1234")"""
)

content = content.replace(
"""    @Test
    fun testP0_4_FailClosedStreamCryptoIntegrity() {""",
"""    @Test
    fun testP0_4_FailClosedStreamCryptoIntegrity() {
        com.example.security.VaultKeyManager.initializeVrkWithPin(context, "1234")
        com.example.security.VaultKeyManager.authorizeWithPin(context, "1234")"""
)

with open("app/src/test/java/com/example/QuantumVaultSecurityHardeningTest.kt", "w") as f:
    f.write(content)
