"""
Resolves conflicts between the tabletop work and the openxr branch: keeps the openxr side, and adds the
tabletop lines and imports from the other side, translated to the openxr branch's names.
usage: python3 resolve_conflicts.py <files with conflict markers>
"""
import re
import sys

TRANSLATE = [
    ('"boolean"', 'ActionType.BOOLEAN'),
    ('"vector1"', 'ActionType.VEC1'),
    ('"vector2"', 'ActionType.VEC2'),
    ('org.vivecraft.client_vr.provider.ControllerType;', 'org.vivecraft.client_vr.provider.control.ControllerType;'),
    ('org.vivecraft.client_vr.provider.openvr_lwjgl.VRInputAction;', 'org.vivecraft.client_vr.provider.control.InputAction;'),
]


def resolve(match):
    ours = match.group(1).split('\n')
    theirs = match.group(2).split('\n')
    added = []
    for line in theirs:
        for a, b in TRANSLATE:
            line = line.replace(a, b)
        # tabletop lines, and imports, which are always safe to merge
        if ('abletop' in line or line.startswith('import ')) and line not in ours:
            added.append(line)
    return '\n'.join(ours + added)


for path in sys.argv[1:]:
    text = open(path).read()
    text, count = re.subn(r'<<<<<<< [^\n]*\n(.*?)\n(?:\|\|\|\|\|\|\| [^\n]*\n.*?\n)?=======\n(.*?)\n>>>>>>> [^\n]*',
                          resolve, text, flags=re.S)
    if '<<<<<<<' in text:
        sys.exit('unresolved conflict left in ' + path)
    open(path, 'w').write(text)
    print('resolved', count, 'conflicts in', path)
