import os

with open('porridge.sh', 'r') as f:
    sh = f.read()

# Add arg parsing
arg_parsing = """FORCE_BUILD=false
ARGS=()

for arg in "$@"; do
    if [ "$arg" = "--build" ]; then
        FORCE_BUILD=true
    else
        ARGS+=("$arg")
    fi
done

# Version requirements"""

sh = sh.replace('# Version requirements', arg_parsing)

# Change jar check
sh = sh.replace('if [ ! -f "$JAR_FILE" ]; then', 'if [ "$FORCE_BUILD" = true ] || [ ! -f "$JAR_FILE" ]; then')

# Change execute
sh = sh.replace('exec "$JAVA_CMD" -jar "$JAR_FILE" "$@"', 'exec "$JAVA_CMD" -jar "$JAR_FILE" "${ARGS[@]}"')

with open('porridge.sh', 'w') as f:
    f.write(sh)


with open('porridge.bat', 'r') as f:
    bat = f.read()

bat_arg_parsing = """set FORCE_BUILD=false
set ARGS=

:parse_args
if "%~1"=="" goto done_args
if "%~1"=="--build" (
    set FORCE_BUILD=true
) else (
    set ARGS=!ARGS! %1
)
shift
goto parse_args
:done_args

set REQUIRED_JAVA_VERSION=17"""

bat = bat.replace('set REQUIRED_JAVA_VERSION=17', bat_arg_parsing)

# Change jar check
# In bat:
# if not exist "%JAR_FILE%" (
bat = bat.replace('if not exist "%JAR_FILE%" (', 'if "!FORCE_BUILD!"=="true" (\n    set DO_BUILD=1\n) else if not exist "%JAR_FILE%" (\n    set DO_BUILD=1\n) else (\n    set DO_BUILD=0\n)\n\nif "!DO_BUILD!"=="1" (')

# Change execute
bat = bat.replace('"%JAVA_CMD%" -jar "%JAR_FILE%" %*', '"%JAVA_CMD%" -jar "%JAR_FILE%" !ARGS!')

with open('porridge.bat', 'w') as f:
    f.write(bat)
