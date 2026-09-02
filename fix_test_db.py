import re

with open("app/src/test/java/com/example/QuantumVaultSecurityHardeningTest.kt", "r") as f:
    content = f.read()

content = content.replace(
"""    @Test
    fun testP0_8_DatabaseNoDestructiveMigrationOnDowngrade() {""",
"""    @Test
    fun testP0_8_DatabaseNoDestructiveMigrationOnDowngrade() {
        com.example.security.VaultKeyManager.initializeVrkWithPin(context, "1234")
        com.example.security.VaultKeyManager.authorizeWithPin(context, "1234")"""
)

with open("app/src/test/java/com/example/QuantumVaultSecurityHardeningTest.kt", "w") as f:
    f.write(content)
