(ns oti.ui.app
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [devtools.core :as devtools]
            [oti.ui.handlers]
            [oti.ui.subs]
            [oti.ui.routes :as routes]
            [oti.ui.views.virkailija-main :as virkailija]
            [oti.ui.registration.main-view :as registration]
            [oti.ui.config :as config]))

(defn dev-setup []
  (when config/debug?
    (enable-console-print!)
    (println "dev mode")
    (devtools/install!)))

(defonce mode-atom (reagent/atom ""))

(defn mount-root []
  (reagent/render (if (= "virkailija" @mode-atom)
                    [virkailija/main-panel]
                    [registration/main-panel])
                  (.getElementById js/document "app")))

(defn init-mode [mode]
  (if (= "virkailija" mode)
    (re-frame/dispatch [:load-user])
    (re-frame/dispatch [:set-language :fi])))

(defn ^:export start [mode]
  (routes/app-routes)
  (re-frame/dispatch-sync [:initialize-db])
  (init-mode mode)
  (dev-setup)
  (reset! mode-atom mode)
  (mount-root))

(defn reload-hook []
  (mount-root))
