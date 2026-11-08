"""Turn MCPConfig's joined.tsrg (obf -> SRG) into obf -> MCP names using an MCP snapshot's CSVs.
Usage: python -I tsrg_mcp.py joined.tsrg csvdir out.tsrg"""
import csv, sys, os

tsrg, csvdir, out = sys.argv[1:4]
names = {}
for f in ('fields.csv', 'methods.csv'):
    with open(os.path.join(csvdir, f), newline='', encoding='utf-8') as fh:
        for row in csv.DictReader(fh):
            names[row['searge']] = row['name']
with open(tsrg, encoding='utf-8') as fin, open(out, 'w', encoding='utf-8', newline='\n') as fo:
    for line in fin:
        line = line.rstrip('\n')
        if line.startswith('\t'):
            parts = line[1:].split(' ')
            parts[-1] = names.get(parts[-1], parts[-1])
            fo.write('\t' + ' '.join(parts) + '\n')
        else:
            fo.write(line + '\n')
