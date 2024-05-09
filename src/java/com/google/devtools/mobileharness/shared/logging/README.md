# Logging tools for all projects.

Base logging utilities to upload logs to GCP.

To upload logs to a Google Cloud project [PROJECT_ID],
* Enable the Cloud Logging API for the project.
* Give the service account the role of Logs Writer.
* Launch the service with flags `--stackdriver_cred_file [CREDENTIAL]
--stackdriver_gcp_project_name [PROJECT_ID] --enable_cloud_logging true`.

