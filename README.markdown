Sleet is an SMTP server with support for the POP3 protocol.

#Requires

 * Java 6
 * Maven 2 or greater (to build)

#Building

Do a Maven package:

    mvn package
    
#Running

1. Edit "dist/sleet/bin/start.sh" to use the host name of your server ("--hostName" argument).
1. `cd dist/sleet/bin`
1. `chmod 744 start.sh`
1. `./start.sh`

The `start.sh` script will start the Sleet SMTP server on port 2550.  Mail submission will run on port 2551 and POP3 will run on port 2552.  The reason why these non-standard ports are used is that I couldn't open connections to the standard ports on my computer (ports 25, 587, and 110 respectively).  To test it out, you can connect to it via telnet:

    telnet localhost <port>

#Arguments

The main class, `sleet.Sleet`, takes a number of command-line arguments.  All arguments are in "long form" for readability.

    --smtp-port=PORT
    The SMTP server port (defaults to 25).
    
    --smtp-msa-port=PORT
    The SMTP mail submission port (defaults to 587).
    
    --pop3-port=PORT
    The POP3 server port (defaults to 110).
    
    --host-name=NAME [required]
    The host name of this server (e.g. myserver.com).
    This is what's used in email addresses destined for and coming from this server.
    
    --database=PATH
    The path to where the database will be stored or "MEM" to use an in-memory
    database (defaults to "sleet-db").
    
    --smtp-inbound-log=PATH
    The path to where inbound SMTP transactions are logged.
    
    --smtp-outbound-log=PATH
    The path to where outbound SMTP transactions are logged.
    
    --smtp-msa-log=PATH
    The path to where inbound SMTP mail submission transactions are logged.
    
    --pop3-log=PATH
    The path to where POP3 transactions are logged.
    
    --version
    Prints the version.
    
    --help
    Prints this help message.