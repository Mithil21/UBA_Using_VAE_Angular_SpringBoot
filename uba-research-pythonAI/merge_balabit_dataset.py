import glob
import os
import pandas as pd

script_dir   = os.path.dirname(os.path.abspath(__file__))
training_dir = os.path.join(script_dir, "Mouse-Dynamics-Challenge", "training_files")

# Files have NO extension — match everything that isn't a directory
files = [
    os.path.join(root, f)
    for root, dirs, filenames in os.walk(training_dir)
    for f in filenames
    if '.' not in f  # session files have no extension
]

print(f"Found {len(files)} session files")

# Columns from the README
col_names = ['record_timestamp', 'client_timestamp', 'button', 'state', 'x', 'y']

dfs = []
for f in files:
    try:
        df = pd.read_csv(f, header=0, names=col_names)
        dfs.append(df)
    except Exception as e:
        print(f"Skipping {f}: {e}")

combined = pd.concat(dfs, ignore_index=True)

output_path = os.path.join(script_dir, "balabit_mouse.csv")
combined.to_csv(output_path, index=False)

print(f"Done — {len(combined)} rows saved to {output_path}")
print(f"Columns: {list(combined.columns)}")