package org.eclipse.packagedrone.testing.server;

import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ServerRunner
{
    private static final Duration START_TIMEOUT = Duration.ofSeconds ( 30 );

    private final int port;

    private Process process;

    private final PrintStream log;

    public ServerRunner ( final int port )
    {
        this.port = port;
        this.log = System.out;
    }

    public void start () throws InterruptedException, IOException
    {
        final String javaHome = System.getProperty ( "java.home" );

        final ProcessBuilder pb = new ProcessBuilder ( "target/instance/server" );

        pb.environment ().put ( "JAVA_HOME", javaHome );

        final Map<String, String> additional = new HashMap<> ();
        makeProcessSystemProperties ( pb, additional );

        pb.inheritIO ();

        this.log.format ( "Starting server: %s%n", pb );
        this.log.flush ();

        this.process = pb.start ();

        this.log.format ( "Started ... %s%n", this.process );
        this.log.flush ();

        waitForPortOpen ();

        this.log.println ( "Port open" );
        Thread.sleep ( 1_000 );
    }

    private void waitForPortOpen () throws InterruptedException
    {
        final Instant start = Instant.now ();
        while ( !isPortOpen () )
        {
            if ( Duration.between ( start, Instant.now () ).compareTo ( START_TIMEOUT ) > 0 )
            {
                this.process.destroyForcibly ();
                throw new IllegalStateException ( "Failed to wait for port" );
            }
            Thread.sleep ( 1_000 );
        }
    }

    public void stop () throws InterruptedException
    {
        this.log.print ( "Stopping server..." );
        this.log.flush ();

        if ( !this.process.destroyForcibly ().waitFor ( 10, TimeUnit.SECONDS ) )
        {
            throw new IllegalStateException ( "Failed to terminate process" );
        }

        this.log.println ( "stopped!" );
    }

    private static void makeProcessSystemProperties ( final ProcessBuilder pb, final Map<String, String> additional )
    {
        final StringBuilder sb = new StringBuilder ();
        for ( final Map.Entry<Object, Object> entry : System.getProperties ().entrySet () )
        {
            if ( entry.getKey () == null || entry.getValue () == null )
            {
                continue;
            }

            final String key = entry.getKey ().toString ();
            final String value = entry.getValue ().toString ();

            if ( key.startsWith ( "org.osgi." ) || key.startsWith ( "drone." ) )
            {
                if ( sb.length () > 0 )
                {
                    sb.append ( ' ' );
                }
                sb.append ( "-D" ).append ( key ).append ( '=' ).append ( value );
            }
        }

        for ( final Map.Entry<String, String> entry : additional.entrySet () )
        {
            final String key = entry.getKey ();
            final String value = entry.getValue ();

            if ( sb.length () > 0 )
            {
                sb.append ( ' ' );
            }
            sb.append ( "-D" ).append ( key ).append ( '=' ).append ( value );
        }

        pb.environment ().put ( "JAVA_OPTS", sb.toString () );
    }

    private boolean isPortOpen ()
    {
        try ( ServerSocket server = new ServerSocket ( this.port ) )
        {
            // there is a slim chance that by doing this, we actually block the other process opening this port
            return false;
        }
        catch ( final IOException e1 )
        {
            return true;
        }
    }

}
