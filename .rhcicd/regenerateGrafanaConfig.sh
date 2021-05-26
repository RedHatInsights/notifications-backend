#!/bin/bash
echo "...Attempting regenerate validate grafana configuration";

#Check for helpful policies-tools/shared/* contents:
SCRIPT=`basename "$0"`;
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )";
POLICIES_TOOLS_HELPER_CONFIG=`realpath "$SCRIPT_DIR/../../policies-tools"`;
if [[ -d "$POLICIES_TOOLS_HELPER_CONFIG" ]]
then
  SHARED="shared";
  . "$POLICIES_TOOLS_HELPER_CONFIG"/"$SHARED"/DefineConstants.sh --source-only
  . "$POLICIES_TOOLS_HELPER_CONFIG"/"$SHARED"/DebugLog.sh --source-only
    [[ "$DEBUG_ENABLED" == "$YES" ]] && DebugLog "SCRIPT_DIR" "$SCRIPT_DIR" "" "FILE";
    [[ "$DEBUG_ENABLED" == "$YES" ]] && DebugLog "POLICIES_TOOLS_ROOT" "$POLICIES_TOOLS_HELPER_CONFIG" "" "FILE";
    [[ "$DEBUG_ENABLED" == "$YES" ]] && DebugLog "DEFINE_CONSTANTS" "$POLICIES_TOOLS_HELPER_CONFIG/$SHARED/DefineConstants.sh" "" "FILE";
fi

# detect name of config file
YAML_FILE=`ls $SCRIPT_DIR | grep -m 1 grafana-dashboard-*.configmap.yaml`;
  [[ "$DEBUG_ENABLED" == "$YES" ]] && DebugLog "YAML_FILE" "$YAML_FILE" "(testing for expected config file)" "FILE";
# load constants
WRAPPER_YAML="wrapper.yaml";
  [[ "$DEBUG_ENABLED" == "$YES" ]] && DebugLog "WRAPPER_YAML" "$WRAPPER_YAML" "(testing for expected yaml wrapper file)" "FILE";
SECTIONS_JSON="sections.json";
  [[ "$DEBUG_ENABLED" == "$YES" ]] && DebugLog "SECTIONS_JSON" "$SECTIONS_JSON" "(testing for expected json sections file)" "FILE";

## regenerate the contents of config file
# generate top
sed -n 1,3p "$WRAPPER_YAML" > "$YAML_FILE";

# Are 'sections' already properly spaced?
SECTIONS_LINES=`wc -l "$SECTIONS_JSON" | cut -d' ' -f1`;
  [[ "$DEBUG_ENABLED" == "$YES" ]] && DebugLog "SECTIONS_LINES" "$SECTIONS_LINES";
SECTION_LINES_ALREADY_SPACED=`awk '/^    /{a++}END{print a}' "$SECTIONS_JSON"`;
  [[ "$DEBUG_ENABLED" == "$YES" ]] && DebugLog "SECTION_LINES_ALREADY_SPACED" "$SECTION_LINES_ALREADY_SPACED";
ALL_SECTIONS_ALREADY_SPACED="false"; [ "$SECTIONS_LINES" == "$SECTION_LINES_ALREADY_SPACED" ] && ALL_SECTIONS_ALREADY_SPACED="true";
  [[ "$DEBUG_ENABLED" == "$YES" ]] && DebugLog "ALL_SECTIONS_ALREADY_SPACED" "$ALL_SECTIONS_ALREADY_SPACED";

# generate json content
while read line; do
  [[ "$ALL_SECTIONS_ALREADY_SPACED" == "true" ]] && echo -e "$line\n" >> "$YAML_FILE";
  [[ "$ALL_SECTIONS_ALREADY_SPACED" == "false" ]] && echo -e "    $line" >> "$YAML_FILE";
done < "$SECTIONS_JSON";

LINE_COUNT=`wc -l "$YAML_FILE" | cut -d' ' -f1`;
  [[ "$DEBUG_ENABLED" == "$YES" ]] && DebugLog "LINE_COUNT" "$LINE_COUNT";

# generate end
sed -n 4,"$LINE_COUNT"p "$WRAPPER_YAML" >> "$YAML_FILE";

if (( $LINE_COUNT > 11 ))
then
  echo "Regeneration of grafana configuration file[$YAML_FILE] IS complete. Starting validation...";

  # launching revalidation: Failure here likely indicates wrapper.yaml, sections.json, or other *.json is not well formed.
  "$SCRIPT_DIR"/validateGrafanaConfig.sh 
fi

