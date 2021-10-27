# Java AMQP SDK log parser tool

Small tool to parse Azure messaging SDK (for Java) log files and send them to Azure Log Analytics for analysis.

## Prerequisites

- Maven
- JDK 11
- Application Insights resource in portal

## Usage

- `mvn clean package`
- `java -jar ./target/log-parser.jar -f /path/to/logs [-l <arg>] [-d] [-r <arg>] [-c <arg>] [-z] [-m <arg>] [-h]`

### Options

```bash
 -f,--file <arg>                 REQUIRED. Log file (directory or zip archive) name
 -l,--layout <arg>               log layout, defaults to "<date> <time> <level> <thread> <class> ". Message is the last one and not specified in the layout.
 -d,--dry-run                    dry run, when set, does nto send data to Log Analytics and parses first 2 lines of each file
 -r,--run-id <arg>               Name to identify this run, by default fileName is used. Parser will add unique id.
 -c,--connection-string <arg>    Azure Monitor connection string (or pass it in APPLICATIONINSIGHTS_CONNECTION_STRING env var). If not set, it will be a dry-run
 -z,--unzip                      Unzip file before processing (will be done if file extension is 'zip')
 -m,--max-lines-per-file <arg>   Max lines to print, none by default
 -h,--help                       Print help
```

### Examples

**Custom log layout**:
`java -jar log-parser.jar -f c:\downloads\logs.zip -l "<date> <time> <level> [<thread>] <class> - " -c InstrumentationKey=secret;IngestionEndpoint=https://westus2-2.in.applicationinsights.azure.com/`

**Print parsed logs**:
`java -jar -Dorg.slf4j.simpleLogger.defaultLogLevel=debug log-parser.jar  -f c:\downloads\logs.log -c InstrumentationKey=secret;IngestionEndpoint=https://westus2-2.in.applicationinsights.azure.com/ -m 10`
when specifying layout, add separator at the end. `date`, `time` and `level` are magic words, other fields are arbitrary.

**Dry run, no data is sent**: `java -jar log-parser.jar -f c:\downloads\logs -d`
