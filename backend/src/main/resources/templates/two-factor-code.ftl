<#--
  The one-time code email.

  Deliberately plain: no images, no external stylesheets, no tracking pixels.
  Mail clients block remote content by default, and a security email that
  renders as a row of broken images is one people learn to distrust. Everything
  here is inline and survives a text-only client.

  Model keys: purposeIsEnrolment, code, expiryMinutes, pharmacyName
-->
<html>
<body style="margin:0;padding:0;background-color:#f4f6f8;font-family:Arial,Helvetica,sans-serif;">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:#f4f6f8;padding:24px 12px;">
    <tr>
      <td align="center">
        <table role="presentation" width="100%" cellpadding="0" cellspacing="0"
               style="max-width:480px;background-color:#ffffff;border-radius:6px;border:1px solid #e1e5ea;">

          <tr>
            <td style="padding:20px 28px;border-bottom:1px solid #e1e5ea;">
              <div style="font-size:17px;font-weight:bold;color:#15616d;">${pharmacyName!"Doc Pharma"}</div>
            </td>
          </tr>

          <tr>
            <td style="padding:28px;">
              <p style="margin:0 0 18px;font-size:15px;color:#2b3440;line-height:1.5;">
                <#if purposeIsEnrolment>
                  Use this code to confirm this email address for signing in.
                <#else>
                  Use this code to finish signing in.
                </#if>
              </p>

              <#-- Letter-spacing so the digits are readable in a glance and
                   hard to mis-transcribe. -->
              <div style="margin:0 0 18px;padding:16px;background-color:#f4f6f8;border-radius:4px;text-align:center;">
                <span style="font-family:'Courier New',Courier,monospace;font-size:30px;font-weight:bold;letter-spacing:7px;color:#15616d;">${code}</span>
              </div>

              <p style="margin:0 0 18px;font-size:13px;color:#5b6773;line-height:1.5;">
                It stops working in ${expiryMinutes} minutes, and can only be used once.
              </p>

              <p style="margin:0;padding:14px;background-color:#fff6f6;border-left:3px solid #c0392b;font-size:13px;color:#2b3440;line-height:1.5;">
                <#-- The part that matters. A code arriving unprompted means
                     somebody else has the password. -->
                <strong>If you did not just try to sign in, tell your administrator.</strong>
                Someone else may know your password. Nobody from Doc Pharma will ever ask you for this code.
              </p>
            </td>
          </tr>

          <tr>
            <td style="padding:14px 28px;border-top:1px solid #e1e5ea;font-size:12px;color:#8793a1;">
              Automatic message — replies to it are not read.
            </td>
          </tr>

        </table>
      </td>
    </tr>
  </table>
</body>
</html>
