<%--
  ~ Copyright 2000-2009 JetBrains s.r.o.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~ http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  --%>

<%@include file="/include.jsp"%>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>

<table class="runnerFormTable">
<l:settingsGroup title="ClearCase Settings">
<tr>
  <th><label for="view-path">View path: <l:star/></label>
  </th>
  <td><props:textProperty name="view-path" className="longField" />
    <span class="error" id="error_view-path"></span></td>
</tr>
<tr>
  <th class="noBorder"><label for="TYPE">Use ClearCase:</label></th>
  <td class="noBorder"><props:selectProperty name="TYPE">
        <props:option value="UCM">UCM</props:option>
        <props:option value="BASE">BASE</props:option>
      </props:selectProperty></td>
</tr>
</l:settingsGroup>
<l:settingsGroup title="Labling settings">
<tr>
  <th><label for="use-global-label">Global labeling:</label></th>
  <td>
    <props:checkboxProperty name="use-global-label" onclick="$('global-labels-vob').disabled = this.checked ? '' : 'disabled'" />
    <label for="use-global-label">Use global labels</label>
  </td>
</tr>
<tr>
  <th><label for="global-labels-vob">Global labels VOB:</label></th>
  <td>
    <props:textProperty name="global-labels-vob" className="longField" disabled="${propertiesBean.properties['use-global-label'] != 'true'}"/>
    <span class="error" id="error_global-labels-vob"></span>
    <div class="smallNote" style="margin-left: 0;">
      Pathname of the VOB tag (whether or not the VOB is mounted) or of any file system object within the VOB (if the VOB is mounted)
    </div>
  </td>
</tr>
</l:settingsGroup>
</table>
