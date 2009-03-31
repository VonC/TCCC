BS.ClearCaseSettings = {
    convertSettings : function() {
        BS.Util.show($('convertSettingsProgressIcon'));

        BS.VcsSettingsForm.clearErrors();
        BS.VcsSettingsForm.disable();

        BS.ajaxRequest("/admin/convertOldCCSettings.html", {
            parameters: {
                "view-path-value": $("view-path").value
            },

            onComplete: function(transport) {
                BS.VcsSettingsForm.enable();

                BS.Util.hide($('convertSettingsProgressIcon'));

                var xml = transport.responseXML;

                if (xml == null) {
                    alert("Error: server response is null");
                    return;
                }

                var firstChild = xml.documentElement.firstChild;

                if (firstChild.nodeName == 'error') {
                    alert("Error: " + firstChild.textContent);
                    return;
                }

                $('oldSettingsRow').style.display = "none";
                $('oldSettingsMessage').style.display = "none";

                var secondChild = firstChild.nextSibling;

                $('view-path').value = "";
                $('cc-view-path').value = firstChild.textContent;
                $('rel-path').value = secondChild.textContent;
            }
        });
    }
};