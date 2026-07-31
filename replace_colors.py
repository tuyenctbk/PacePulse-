import os
import re

replacements = {
    r'\bNeonLime\b': 'MaterialTheme.colorScheme.primary',
    r'\bElectricCyan\b': 'MaterialTheme.colorScheme.secondary',
    r'\bPulseRed\b': 'MaterialTheme.colorScheme.error',
    r'\bOledBlack\b': 'MaterialTheme.colorScheme.background',
    r'\bDarkSurfaceVariant\b': 'MaterialTheme.colorScheme.surfaceVariant',
    r'\bDarkSurface\b': 'MaterialTheme.colorScheme.surface',
    r'\bTextPrimary\b': 'MaterialTheme.colorScheme.onBackground',
    r'\bTextSecondary\b': 'MaterialTheme.colorScheme.onSurfaceVariant',
    r'\bTextMuted\b': 'MaterialTheme.colorScheme.onSurfaceVariant', # Simplified
}

# we need to be careful with OledBlack used as text color on primary button.
# If background is primary, text is onPrimary.

def process_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    # Special handling for OledBlack when it's on primary:
    content = content.replace('color = if (isSelected) OledBlack', 'color = if (isSelected) MaterialTheme.colorScheme.onPrimary')
    content = content.replace('contentColor = OledBlack', 'contentColor = MaterialTheme.colorScheme.onPrimary')
    content = content.replace('tint = OledBlack', 'tint = MaterialTheme.colorScheme.onPrimary')
    content = content.replace('color = OledBlack', 'color = MaterialTheme.colorScheme.onPrimary')
    content = content.replace('checkedThumbColor = OledBlack', 'checkedThumbColor = MaterialTheme.colorScheme.onPrimary')

    # Remove imports
    content = re.sub(r'import com\.example\.ui\.theme\.(NeonLime|ElectricCyan|PulseRed|OledBlack|DarkSurfaceVariant|DarkSurface|TextPrimary|TextSecondary|TextMuted)\n', '', content)
    
    # Add MaterialTheme import if not exists
    if 'androidx.compose.material3.MaterialTheme' not in content:
        content = content.replace('import androidx.compose.runtime.Composable', 'import androidx.compose.material3.MaterialTheme\nimport androidx.compose.runtime.Composable')

    for pattern, replacement in replacements.items():
        content = re.sub(pattern, replacement, content)

    with open(filepath, 'w') as f:
        f.write(content)

for root, _, files in os.walk('./app/src/main/java/com/example/ui'):
    for file in files:
        if file.endswith('.kt') and 'theme' not in root:
            process_file(os.path.join(root, file))

