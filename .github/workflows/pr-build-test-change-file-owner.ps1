$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

# Change the permission of build results if the user ID is provided
if ($null -ne ${env:GOBLEY_PR_BUILD_TEST_USER}) {
    & "chown" ${env:GOBLEY_PR_BUILD_TEST_USER} -R .;
}
