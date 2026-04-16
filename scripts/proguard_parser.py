import re
from collections import Counter

log_path = r"d:\lora\qingliao\CharRoom\build\compose\logs\proguardReleaseJars\java-2026-04-16-18-00-30-out.txt"
text = open(log_path, encoding='utf-8', errors='ignore').read()

patterns = [
    re.compile(r"can't find referenced class\s+([\w.$]+)"),
    re.compile(r"can't find superclass or interface\s+([\w.$]+)"),
    re.compile(r"in (?:library|program) class\s+([\w.$]+)"),
    re.compile(r"can't find referenced method .* in (?:library|program) class\s+([\w.$]+)")
]

matches = []
for pat in patterns:
    matches += pat.findall(text)

# normalize (strip generics/inner class markers after $)
normalized = []
for m in matches:
    # remove trailing punctuation
    m = m.strip().rstrip('.,;:')
    # strip anything after '<' (generics) or '>'
    m = re.split(r'[<>\'"\(\)]', m)[0]
    # remove lambda-like suffixes
    if '$' in m:
        m = m.split('$')[0]
    normalized.append(m)

c = Counter(normalized)

# package grouping heuristic
pkg_counter = Counter()
for cls, cnt in c.items():
    parts = cls.split('.')
    if not parts:
        pkg = cls
    else:
        if parts[0] in ('com','org','net','io') and len(parts) >= 3:
            pkg = '.'.join(parts[:3])
        elif len(parts) >= 2:
            pkg = '.'.join(parts[:2])
        else:
            pkg = parts[0]
    pkg_counter[pkg] += cnt

# print top results
print('Top missing classes (by count):')
for cls, cnt in c.most_common(50):
    print(f'{cnt:4d}  {cls}')

print('\nTop missing packages (by count, heuristic):')
for pkg, cnt in pkg_counter.most_common(50):
    print(f'{cnt:4d}  {pkg}')

print('\nSuggested -dontwarn rules (top packages):')
for pkg, cnt in pkg_counter.most_common(40):
    # convert heuristic pkg back to pattern
    print(f"-dontwarn {pkg}.**")

# exit status
print('\nParsed log:', log_path)