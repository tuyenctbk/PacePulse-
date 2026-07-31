import os
import re

def process_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    # Add MaterialTheme import if not exists
    if 'Color.White.copy' in content and 'androidx.compose.material3.MaterialTheme' not in content:
        content = content.replace('import androidx.compose.runtime.Composable', 'import androidx.compose.material3.MaterialTheme\nimport androidx.compose.runtime.Composable')

    content = content.replace('Color.White.copy', 'MaterialTheme.colorScheme.onSurface.copy')

    with open(filepath, 'w') as f:
        f.write(content)

for root, _, files in os.walk('./app/src/main/java/com/example/ui'):
    for file in files:
        if file.endswith('.kt') and 'theme' not in root:
            process_file(os.path.join(root, file))

