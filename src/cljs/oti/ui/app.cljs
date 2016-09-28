(ns oti.ui.app
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [devtools.core :as devtools]
            [oti.ui.handlers]
            [oti.ui.subs]
            [oti.ui.routes :as routes]
            [oti.ui.views.main :as views]
            [oti.ui.views.applicant :as applicant]
            [oti.ui.config :as config]))

(defn dev-setup []
  (when config/debug?
    (enable-console-print!)
    (println "dev mode")
    (devtools/install!)))

(defn mount-root [mode]
  (reagent/render (if (= "virkailija" mode)
                    [views/main-panel]
                    [applicant/main-panel])
                  (.getElementById js/document "app")))

(defn ^:export start [mode]
  (routes/app-routes)
  (re-frame/dispatch-sync [:initialize-db])
  (re-frame/dispatch [:load-user])
  (dev-setup)
  (mount-root mode)
  (println mode))
