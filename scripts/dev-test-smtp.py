#!/usr/bin/env python3
"""
Tests the two-factor mailbox directly, outside the application.

    export TWOFACTOR_MAIL_PASSWORD='...'
    scripts/dev-test-smtp.py you@example.com

Nothing to do with Spring or the backend - it connects to the mail server the
same way the application does and reports exactly what the server says. That
separates "the mailbox or its settings are wrong" from "the application is
wrong", which the generic 503 the browser shows cannot tell you.

The password is read from the environment and never printed.
"""

import os
import smtplib
import ssl
import sys
from email.message import EmailMessage

HOST = os.environ.get("TWOFACTOR_MAIL_HOST", "send.one.com")
USER = os.environ.get("TWOFACTOR_MAIL_USERNAME", "security@docpharmkenya.com")
PASSWORD = os.environ.get("TWOFACTOR_MAIL_PASSWORD", "")

# 465 is implicit SSL, what the application is configured for. 587 is STARTTLS,
# which many providers prefer and some require. Both are tried so a wrong port
# shows up as "this one works" rather than a bare failure.
PORTS = [(465, "SSL"), (587, "STARTTLS")]


def attempt(port, mode, recipient):
    label = "  port %-4s (%-8s)" % (port, mode)
    try:
        context = ssl.create_default_context()
        if mode == "SSL":
            server = smtplib.SMTP_SSL(HOST, port, timeout=15, context=context)
        else:
            server = smtplib.SMTP(HOST, port, timeout=15)
            server.starttls(context=context)

        with server:
            server.login(USER, PASSWORD)

            message = EmailMessage()
            message["Subject"] = "Doc Pharma SMTP test"
            message["From"] = USER
            message["To"] = recipient
            message["Auto-Submitted"] = "auto-generated"
            message.set_content(
                "If you are reading this, %s can send mail on port %s.\n"
                "Two-factor codes will work with these settings." % (USER, port))
            server.send_message(message)

        print("%s  SENT - check %s, including the spam folder" % (label, recipient))
        return True

    except smtplib.SMTPAuthenticationError as e:
        print("%s  REFUSED THE PASSWORD: %s" % (label, e.smtp_error.decode(errors="replace")[:120]))
        print("%s  -> wrong password, or the mailbox is not enabled for SMTP sending" % (" " * len(label)))
    except smtplib.SMTPSenderRefused as e:
        print("%s  REFUSED THE SENDER: %s" % (label, str(e)[:120]))
        print("%s  -> the From address must match the mailbox that signed in" % (" " * len(label)))
    except smtplib.SMTPRecipientsRefused as e:
        print("%s  REFUSED THE RECIPIENT: %s" % (label, str(e)[:120]))
    except (smtplib.SMTPException, OSError, ssl.SSLError) as e:
        print("%s  %s: %s" % (label, type(e).__name__, str(e)[:120]))
    return False


def main():
    if len(sys.argv) < 2:
        sys.exit("usage: dev-test-smtp.py you@example.com")
    if not PASSWORD:
        sys.exit("TWOFACTOR_MAIL_PASSWORD is not set in this shell.")

    recipient = sys.argv[1]

    # Catch a password that was never really set, before the server does.
    # Copying an example from instructions is the usual cause, and an SMTP
    # rejection or a unicode traceback is a confusing way to find that out.
    try:
        PASSWORD.encode("ascii")
    except UnicodeEncodeError:
        sys.exit("\n  The password contains non-ASCII characters (%r).\n"
                 "  If you pasted an example from instructions, replace it with the real\n"
                 "  password - keep the single quotes, drop everything else.\n"
                 % [c for c in PASSWORD if ord(c) > 127][:3])

    if "password" in PASSWORD.lower() or PASSWORD.startswith("the-"):
        sys.exit("\n  TWOFACTOR_MAIL_PASSWORD still looks like a placeholder: %d characters\n"
                 "  starting %r. Set the real password instead.\n" % (len(PASSWORD), PASSWORD[:4]))

    print("\n  host: %s\n  as:   %s\n  to:   %s\n  password: %d characters\n"
          % (HOST, USER, recipient, len(PASSWORD)))

    if any(attempt(port, mode, recipient) for port, mode in PORTS):
        print("\n  At least one worked. Use that port in twofactor.mail.port.\n")
    else:
        print("\n  Neither port worked. The message above says why - most often a\n"
              "  wrong password, or SMTP not enabled for this mailbox at one.com.\n")


if __name__ == "__main__":
    main()
