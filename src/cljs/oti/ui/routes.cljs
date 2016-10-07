(ns oti.ui.routes
    (:require-macros [secretary.core :refer [defroute]])
  (:require [secretary.core :as secretary]
            [re-frame.core :as re-frame]
            [pushy.core :as pushy]
            [oti.routing :as routing]))

(def virkailija-routes
  [{:view :exam-sessions-panel :url routing/virkailija-root :text "Tutkintotapahtumat"}
   {:view :new-exam-session-panel :url (routing/v-route "/tutkintotapahtuma")}
   {:action :load-exam-session-editor :url (re-pattern (routing/v-route "/tutkintotapahtuma/(\\d+)"))}
   {:view :registrations-panel :url (routing/v-route "/ilmoittautumiset") :text "Ilmoittautumiset"}
   {:view :registrations-panel :url (re-pattern (routing/v-route "/ilmoittautumiset/(\\d+)"))}])

(def history (atom nil))

(defn app-routes []
  (secretary/set-config! :prefix "/")

  (doseq [{:keys [action view url]} virkailija-routes]
    (secretary/add-route! url (fn [params]
                                (re-frame/dispatch
                                  (if action
                                    [action params]
                                    [:set-active-panel view params])))))

  ;; Pushy handles the HTML5 history based navigation
  (reset! history (pushy/pushy secretary/dispatch!
                               (fn [x]
                                 (when (secretary/locate-route x) x))))
  (pushy/start! @history))

(defn redirect [target]
  (pushy/set-token! @history target)
  (secretary/dispatch! target))
