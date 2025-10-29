#!/bin/bash
echo ""
echo "--- Debugging ADC Credentials inside VM ---"
echo ""

echo "1. Checking GOOGLE_APPLICATION_CREDENTIALS env var:"
if [ -n "$GOOGLE_APPLICATION_CREDENTIALS" ]; then
  echo "   GOOGLE_APPLICATION_CREDENTIALS is SET to: $GOOGLE_APPLICATION_CREDENTIALS"
  if [ -f "$GOOGLE_APPLICATION_CREDENTIALS" ]; then
    echo "   The file exists."
    # Optionally, print type of key, but be careful about sensitive details
    grep '"type":' "$GOOGLE_APPLICATION_CREDENTIALS" || echo "   Could not determine key type."
  else
    echo "   WARNING: The file $GOOGLE_APPLICATION_CREDENTIALS does NOT exist."
  fi
else
  echo "   GOOGLE_APPLICATION_CREDENTIALS is NOT SET."
fi
echo ""

echo "2. Checking for gcloud user ADC file:"
ADC_FILE="$HOME/.config/gcloud/application_default_credentials.json"
if [ -f "$ADC_FILE" ]; then
  echo "   Found gcloud ADC file: $ADC_FILE"
  # Optionally, print type of key
  grep '"type":' "$ADC_FILE" || echo "   Could not determine key type."
else
  echo "   No gcloud ADC file found at $ADC_FILE"
fi
echo ""

echo "3. gcloud auth status:"
gcloud auth list
echo ""
gcloud config list account --format="value(core.account)"
echo ""

echo "4. VM Default Service Account from Metadata Server:"
SA_EMAIL=$(curl -H "Metadata-Flavor: Google" "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/email" -s -m 1)
echo "   Email: $SA_EMAIL"
echo ""

echo "5. VM Default Service Account SCOPES from Metadata Server:"
curl -H "Metadata-Flavor: Google" "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/scopes" -s -m 1
echo ""
echo "--- End Debugging ADC Credentials ---"
