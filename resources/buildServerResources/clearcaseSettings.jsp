<%--
  ~ Copyright 2000-2008 JetBrains s.r.o.
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
<tr> <!--TODO: add checkbox "Use global labels" which enables the field. Check styles.-->
  <th><label for="view-path">Global labels VOB:</label>
  </th>
  <td><props:textProperty name="global-labels-vob" className="longField" />
  </td>
</tr>
</l:settingsGroup>
</table>
