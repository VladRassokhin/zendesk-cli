# zendesk-cli
Simple CLI for ZenDesk


# Usage

## Running
Run `java -jar zendesk-cli-1.0-SNAPSHOT-all.jar [GLOBAL_OPTIONS] COMMAND [OPTIONS] [ARGUMENTS]`
Simple no command run will list all supported commands

## Config file
Connection config file could be specified as `-config PATH_TO_CONFIG_FILE` or default `$HOME/.zendesk-cli-config` file would be used (if exists)

Config format is simple: one argument per line.
Example of config:
```text
-host
support.zendesk.com
-username
guest
-password
********
```

## Commands

### `fetch-attachments`

It, well, will fetch all attachments of ticket info directory `$HOME/ZenDesk/ZD-$TICKET_ID`

Accepts either `-id TICKET_ID` or simple list of ticket ids,  e.g. `12345 67890`