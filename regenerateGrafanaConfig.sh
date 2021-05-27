#!/bin/bash
echo "...Attempting regenerate validate grafana configuration";

#Check for helpful policies-tools/shared/* contents:
SCRIPT=`basename "$0"`;
RHCICD=".rhcicd";
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )";
POLICIES_TOOLS_HELPER_CONFIG=`realpath "$SCRIPT_DIR/../policies-tools"`;
DBG="false";
if [[ -d "$POLICIES_TOOLS_HELPER_CONFIG" ]]
then
  SHARED="shared";
  . "$POLICIES_TOOLS_HELPER_CONFIG"/"$SHARED"/DefineConstants.sh --source-only
  . "$POLICIES_TOOLS_HELPER_CONFIG"/"$SHARED"/DebugLog.sh --source-only
    [[ "$DEBUG_ENABLED" == "$YES" ]] && DebugLog "SCRIPT_DIR" "$SCRIPT_DIR" "" "FILE";
    [[ "$DEBUG_ENABLED" == "$YES" ]] && DebugLog "POLICIES_TOOLS_ROOT" "$POLICIES_TOOLS_HELPER_CONFIG" "" "FILE";
    [[ "$DEBUG_ENABLED" == "$YES" ]] && DebugLog "DEFINE_CONSTANTS" "$POLICIES_TOOLS_HELPER_CONFIG/$SHARED/DefineConstants.sh" "" "FILE";
    DBG="true";
fi

# detect name of config file
YAML_FILE=`ls "$SCRIPT_DIR/$RHCICD/" | grep -m 1 grafana-dashboard-*`;
  [[ "$DEBUG_ENABLED" == "$YES" ]] && [[ $DBG == "true" ]] && DebugLog "YAML_FILE" "$SCRIPT_DIR/$RHCICD/$YAML_FILE" "(testing for expected config file)" "FILE";
# load constants
WRAPPER_YAML="wrapper.yaml";
  [[ "$DEBUG_ENABLED" == "$YES" ]] && [[ $DBG == "true" ]] && DebugLog "WRAPPER_YAML" "$SCRIPT_DIR/$RHCICD/$WRAPPER_YAML" "(testing for expected yaml wrapper file)" "FILE";
SECTIONS_JSON="sections.json";
  [[ "$DEBUG_ENABLED" == "$YES" ]] && [[ $DBG == "true" ]] && DebugLog "SECTIONS_JSON" "$SCRIPT_DIR/$RHCICD/$SECTIONS_JSON" "(testing for expected json sections file)" "FILE";

## regenerate the contents of config file
# generate top
sed -n 1,3p "$SCRIPT_DIR/$RHCICD/$WRAPPER_YAML" > "$SCRIPT_DIR/$RHCICD/$YAML_FILE";

## Are 'sections' already properly spaced?
SECTIONS_LINES=`wc -l "$SCRIPT_DIR/$RHCICD/$SECTIONS_JSON" | cut -d' ' -f1`;
  [[ "$DEBUG_ENABLED" == "$YES" ]] && [[ $DBG == "true" ]] && DebugLog "SECTIONS_LINES" "$SECTIONS_LINES";

SECTION_LINES_ALREADY_SPACED=`awk '/^    /{a++}END{print a}' "$SCRIPT_DIR/$RHCICD/$SECTIONS_JSON"`;
  [[ "$DEBUG_ENABLED" == "$YES" ]] && [[ $DBG == "true" ]] && DebugLog "SECTION_LINES_ALREADY_SPACED" "$SECTION_LINES_ALREADY_SPACED";

ALL_SECTIONS_ALREADY_SPACED="false"; [ "$SECTIONS_LINES" == "$SECTION_LINES_ALREADY_SPACED" ] && ALL_SECTIONS_ALREADY_SPACED="true";
  [[ "$DEBUG_ENABLED" == "$YES" ]] && [[ $DBG == "true" ]] && DebugLog "ALL_SECTIONS_ALREADY_SPACED" "$ALL_SECTIONS_ALREADY_SPACED";

## generate json content: NOTE: don't name the variable in while loop. Causes hidden line stripping. Ugh.
while read -r; do
  if [[ "$ALL_SECTIONS_ALREADY_SPACED" == "true" ]]
  then
    printf '%s\n' "$REPLY" >> "$SCRIPT_DIR/$RHCICD/$YAML_FILE";
  else
    printf '%s\n' "    $REPLY" >> "$SCRIPT_DIR/$RHCICD/$YAML_FILE";
  fi
done < $SCRIPT_DIR/$RHCICD/$SECTIONS_JSON

LINE_COUNT=`wc -l "$SCRIPT_DIR/$RHCICD/$YAML_FILE" | cut -d' ' -f1`;
  [[ "$DEBUG_ENABLED" == "$YES" ]] && [[ $DBG == "true" ]] && DebugLog "LINE_COUNT" "$LINE_COUNT";

# generate end
sed -n 4,"$LINE_COUNT"p "$SCRIPT_DIR/$RHCICD/$WRAPPER_YAML" >> "$SCRIPT_DIR/$RHCICD/$YAML_FILE";

if (( $LINE_COUNT > 11 ))
then
  echo "Regeneration of grafana configuration file[$YAML_FILE] IS complete. Starting validation...";

  # launching revalidation: Failure here likely indicates wrapper.yaml, sections.json, or other *.json is not well formed.
  "$SCRIPT_DIR"/validateGrafanaConfig.sh 
fi

