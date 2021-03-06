/**
 * Copyright (C) 2012 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.dashboard.ui.components;

import org.jboss.dashboard.DataDisplayerServices;
import org.jboss.dashboard.commons.cdi.CDIBeanLocator;
import org.jboss.dashboard.kpi.KPI;
import org.jboss.dashboard.ui.DashboardListener;
import org.jboss.dashboard.ui.Dashboard;
import org.jboss.dashboard.ui.NavigationManager;
import org.jboss.dashboard.workspace.Panel;
import org.jboss.dashboard.workspace.PanelInstance;
import org.jboss.dashboard.workspace.Section;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import javax.enterprise.context.SessionScoped;
import javax.inject.Inject;

/**
 * Dashboard handler.
 */
@SessionScoped
public class DashboardHandler implements Serializable {

    public final static String KPI_CODE = "kpicode";

    /**
     * Get the instance for the current session.
     */
    public static DashboardHandler lookup() {
        return CDIBeanLocator.getBeanByType(DashboardHandler.class);
    }

    protected Logger log = LoggerFactory.getLogger(DashboardHandler.class);

    /**
     * Dashboards displayed by the user.
     */
    protected Map<Long, Dashboard> dashboards;

    /**
     * Currently accessed dashboard.
     */
    protected Dashboard currentDashboard;

    /**
     * The dashboard listener
     */
    protected DashboardListener listener;

    public DashboardHandler() {
        dashboards = new HashMap<Long, Dashboard>();
        currentDashboard = null;
        listener = new DashboardListener() {
            public void drillDownPerformed(Dashboard parent, Dashboard child) {
                currentDashboard = child;
            }
            public void drillUpPerformed(Dashboard parent, Dashboard child) {
                currentDashboard = parent;
            }
        };
    }

    /**
     * Get the KPI rendered by a given panel.
     */
    public KPI getKPI(Panel panel) {
        if (panel.getInstance() == null) return null;
        if (panel.getRegion() == null) return null;
        if (!(panel.getInstance().getProvider().getDriver().getClass().getName().contains("KPIDriver"))) return null;

        return getKPI(panel.getInstance());
    }

    /**
     * Get the KPI configured for the specified panel.
     * @return null if any KPI has been selected.
     */
    public KPI getKPI(PanelInstance panelInstance) {
        String kpiCode = panelInstance.getParameterValue(KPI_CODE);
        try {
            if (kpiCode == null || kpiCode.trim().equals("")) return null;
            return DataDisplayerServices.lookup().getKPIManager().getKPIByCode(kpiCode);
        } catch (Exception e) {
            log.error("Can not retrieve selected KPI: " + kpiCode, e);
            return null;
        }
    }

    /**
     * Check if the specified section is a dashboard.
     */
    public boolean containsKPIs(Section section) {
        for (Panel panel : section.getPanels()) {
            if (panel.getInstance().getProvider().getDriver().getClass().getName().contains("KPIDriver")) return true;
        }
        return false;
    }

    /**
     * Get the dashboard for the specified page.
     */
    public synchronized Dashboard getDashboard(Section section) {
        if (section == null) return null;

        // Return an existent dashboard. 
        Long key = section.getDbid();
        if (dashboards.containsKey(key)) return dashboards.get(key);

        // Initialize a dashboard instance for the section.
        Dashboard dashboard = new Dashboard();
        dashboard.setSection(section);
        dashboard.addListener(listener);
        dashboards.put(key, dashboard);

        // Init the dashboard (the related data sets will be loaded).
        dashboard.init();
        return dashboard;
    }

    /**
     * Get the dashboard for the current page.
     */
    public synchronized Dashboard getCurrentDashboard() {
        NavigationManager navMgr = NavigationManager.lookup();
        Dashboard dashboard = getDashboard(navMgr.getCurrentSection());
        if (dashboard == null) return null;  // When a section is being deleted the current section is null.
        
        if (currentDashboard == null) return currentDashboard = dashboard;
        if (dashboard.equals(currentDashboard)) return currentDashboard;

        // If the dashboard has been abandoned then re-initialize it when coming back.
        currentDashboard = dashboard;
        dashboard.init();
        return dashboard;
    }
}