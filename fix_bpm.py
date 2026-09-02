import re

with open("app/src/main/java/com/example/security/BiometricPromptManager.kt", "r") as f:
    content = f.read()

content = content.replace(
"""        showPrompt(activity, "Vault Biometric Unlock", "Hardware-authenticated cryptographic unlock", cryptoObject, false, onResult)""",
"""        showPrompt(activity, "Vault Biometric Unlock", "Hardware-authenticated cryptographic unlock", cryptoObject, false, onResult)"""
)

# Wait, the error is:
# e: file:///app/applet/app/src/main/java/com/example/security/BiometricPromptManager.kt:123:117 Argument type mismatch: actual type is 'Function1<BiometricPromptManager.AuthResult, Unit>', but 'Boolean' was expected.

# Because `showPrompt` takes `isEnrollment: Boolean = false`, and if we call:
# `showPrompt(activity, "...", "...", cryptoObject, onResult)` it passes the lambda as the 5th argument.

# Let's fix it by being explicit with named arguments or passing all arguments.
content = content.replace(
"""        showPrompt(activity, "Vault Biometric Unlock", "Hardware-authenticated cryptographic unlock", cryptoObject, false, onResult)""",
"""        showPrompt(activity, "Vault Biometric Unlock", "Hardware-authenticated cryptographic unlock", cryptoObject, isEnrollment = false, onResult = onResult)"""
)

content = content.replace(
"""        showPrompt(activity, "Vault Biometric Unlock", "Hardware-authenticated cryptographic unlock", cryptoObject, onResult)""",
"""        showPrompt(activity, "Vault Biometric Unlock", "Hardware-authenticated cryptographic unlock", cryptoObject, isEnrollment = false, onResult = onResult)"""
)


content = content.replace(
"""        showPrompt(activity, "Enroll Biometric Unlock", "Authenticate to provision hardware vault key", cryptoObject, true) { result ->""",
"""        showPrompt(activity, "Enroll Biometric Unlock", "Authenticate to provision hardware vault key", cryptoObject, isEnrollment = true) { result ->"""
)

with open("app/src/main/java/com/example/security/BiometricPromptManager.kt", "w") as f:
    f.write(content)
