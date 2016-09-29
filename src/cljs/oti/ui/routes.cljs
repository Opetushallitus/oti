(ns oti.ui.routes
    (:require-macros [secretary.core :refer [defroute]])
  (:require [secretary.core :as secretary]
            [re-frame.core :as re-frame]
            [pushy.core :as pushy]
            [oti.routing :as routing]))

(def virkailija-routes
  [{:view :exam-sessions-panel :url routing/virkailija-root :text "Tutkintotapahtumat"}
   {:view :new-exam-session-panel :url (routing/v-route "/tutkintotapahtuma")}
   {:view :students-panel :url (routing/v-route "/henkilot") :text "Henkilötiedot"}])

(def history (atom nil))

(defn app-routes []
  (secretary/set-config! :prefix "/")

  (doseq [{:keys [view url]} virkailija-routes]
    (secretary/add-route! url #(re-frame/dispatch [:set-active-panel view])))

  ;; Pushy handles the HTML5 history based navigation
  (reset! history (pushy/pushy secretary/dispatch!
                               (fn [x]
                                 (when (secretary/locate-route x) x))))
  (pushy/start! @history))

(defn redirect [target]
  (pushy/set-token! @history target)
  (secretary/dispatch! target))
