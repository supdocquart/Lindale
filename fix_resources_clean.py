import os
import re

def fix_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    original_content = content

    # 1. Delete R import
    content = re.sub(r'import com\.metrolist\.music\.R\n?', '', content)

    # 2. Add Res imports if not present, right after package declaration
    if 'import metrolist.shared.generated.resources.*' not in content:
        content = re.sub(
            r'(package [a-zA-Z0-9_.]+)\n', 
            r'\1\nimport metrolist.shared.generated.resources.*\nimport org.jetbrains.compose.resources.stringResource\nimport org.jetbrains.compose.resources.painterResource\nimport org.jetbrains.compose.resources.pluralStringResource\n', 
            content
        )

    # 3. AndroidX compose res -> JetBrains compose res (by removing the explicit android ones if they exist)
    content = re.sub(r'import androidx\.compose\.ui\.res\.stringResource\n?', '', content)
    content = re.sub(r'import androidx\.compose\.ui\.res\.painterResource\n?', '', content)
    content = re.sub(r'import androidx\.compose\.ui\.res\.pluralStringResource\n?', '', content)
    content = re.sub(r'import androidx\.compose\.ui\.res\.vectorResource\n?', '', content)

    # 4. R. -> Res.
    content = re.sub(r'\bR\.string\.', 'Res.string.', content)
    content = re.sub(r'\bR\.drawable\.', 'Res.drawable.', content)
    content = re.sub(r'\bR\.plurals\.', 'Res.plurals.', content)

    # 5. Remove 'id =' in resource calls
    content = re.sub(r'stringResource\(\s*id\s*=\s*', 'stringResource(', content)
    content = re.sub(r'painterResource\(\s*id\s*=\s*', 'painterResource(', content)
    content = re.sub(r'pluralStringResource\(\s*id\s*=\s*', 'pluralStringResource(', content)

    # 6. @DrawableRes icon: Int -> icon: DrawableResource
    content = re.sub(r'@DrawableRes\s+([a-zA-Z0-9_]+)\s*:\s*Int', r'\1: org.jetbrains.compose.resources.DrawableResource', content)
    content = re.sub(r'@StringRes\s+([a-zA-Z0-9_]+)\s*:\s*Int', r'\1: org.jetbrains.compose.resources.StringResource', content)

    if content != original_content:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        return True
    return False

modified_count = 0
for root, dirs, files in os.walk('shared/src/commonMain/kotlin'):
    for file in files:
        if file.endswith('.kt'):
            if fix_file(os.path.join(root, file)):
                modified_count += 1

print(f"Fixed resources in {modified_count} files.")
