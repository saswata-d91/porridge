import re

def fix_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    # Replace ProcessBuilder("sh", "-c", cliCmd) with ProcessBuilder(cliCmd.split(" ")) - roughly.
    # Actually, it's safer to just explain to the user first.
    pass
