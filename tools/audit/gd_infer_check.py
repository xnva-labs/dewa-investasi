import re, sys

TYPED_CALLS = ["float","int","String","str","bool","Vector2","Vector3","Vector2i","Vector3i","Color","Rect2","Rect2i",
  "Array","Dictionary","PackedStringArray","PackedVector2Array","minf","maxf","clampf","mini","maxi","clampi","absf","absi",
  "roundi","floori","ceili","roundf","floorf","ceilf","lerpf","sin","cos","tan","sqrt","pow","randf","randf_range","randi",
  "randi_range","len","range","snappedf","snappedi","fposmod","posmod","deg_to_rad","rad_to_deg","atan2","signf","signi","typeof"]
RISKY_CALLS = ["max","min","clamp","abs","round","floor","ceil","lerp","sign","snapped","wrap","get","pop_front","pop_back",
  "front","back","pick_random","duplicate","values","slice","map","filter","reduce","pop_at","min","max"]

def strip_strings(s):
    out=[];i=0;q=None
    while i<len(s):
        c=s[i]
        if q:
            if c=='\\': i+=2; continue
            if c==q: q=None; out.append('"')
            i+=1; continue
        if c in '"\'': q=c; out.append('"'); i+=1; continue
        if c=='#': break
        out.append(c); i+=1
    return ''.join(out)

def remove_typed_calls(e):
    # repeatedly replace NAME( ... ) of typed converters with 0
    changed=True
    while changed:
        changed=False
        for name in TYPED_CALLS:
            for m in re.finditer(r'(?<![A-Za-z0-9_.])'+name+r'\(', e):
                start=m.end()-1; depth=0
                for j in range(start,len(e)):
                    if e[j]=='(': depth+=1
                    elif e[j]==')':
                        depth-=1
                        if depth==0:
                            e=e[:m.start()]+'0'+e[j+1:]; changed=True; break
                if changed: break
            if changed: break
    return e

def analyse(path):
    findings=[]
    for no,raw in enumerate(open(path,encoding='utf-8').read().split('\n'),1):
        m=re.match(r'^\s*(?:var|const)\s+([A-Za-z_][A-Za-z0-9_]*)\s*:=\s*(.+)$', raw)
        if not m: continue
        name,rhs=m.group(1),strip_strings(m.group(2))
        e=remove_typed_calls(rhs)
        reasons=[]
        if re.search(r'[A-Za-z_)\]]\s*\[', e): reasons.append('index/[] → Variant')
        for rc in RISKY_CALLS:
            if re.search(r'(?<![A-Za-z0-9_])'+rc+r'\(', e) and rc not in ('get',) or (rc=='get' and re.search(r'\.get\(', e)):
                reasons.append(rc+'()')
        if re.match(r'^\s*(null)\b', e): reasons.append('null')
        if reasons:
            findings.append((no,name,raw.strip()[:170],sorted(set(reasons))))
    return findings

if __name__=='__main__':
    for p in sys.argv[1:]:
        res=analyse(p)
        print(f"{p}: {len(res)} temuan")
        for no,name,line,why in res:
            print(f"  L{no}: {line}\n        ↳ {', '.join(why)}")
