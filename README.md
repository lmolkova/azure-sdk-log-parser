# Java AMQP SDK log parser tool

Small tool to parse Azure messaging SDK (for Java) log files and send them to Azure Log Analytics for analysis.

## Prerequisites

- Maven
- JDK 11
- Application Insights resource

## Usage

- `mvn package`
- Depending on what format your log lines are in, you'll use either the "plain" or "json" command.
  - If your log lines are plaintext: `java ./target/log-parser.jar plain [command options] -f path-to-log.log` 
  - If your log lines are JSON objects: `java ./target/log-parser.jar json [command options] -f path-to-json-log.log`

See HELP for full documentation, default values, and available options:
- `java ./target/log-parser.jar --help`

### Plaintext Log Examples

If your log is a series of plaintext lines.

**Custom log layout**:
`java -jar log-parser.jar plain -f c:\downloads\logs.zip -l "<date> <time> <level> [<thread>] <class> - " -c InstrumentationKey=secret;IngestionEndpoint=https://westus2-2.in.applicationinsights.azure.com/`

**Print parsed logs**:

`java -jar -Dorg.slf4j.simpleLogger.defaultLogLevel=debug log-parser.jar plain -f c:\downloads\logs.log -c InstrumentationKey=secret;IngestionEndpoint=https://westus2-2.in.applicationinsights.azure.com/ -m 10`
when specifying layout, add separator at the end. `date`, `time` and `level` are magic words. See `--help` to see full list of supported keywords.

**Dry run, no data is sent**:
`java -jar log-parser.jar plain -f c:\downloads\logs -d`

### Json Log examples

See `java ./target/log-parser.jar --help` for all examples. 