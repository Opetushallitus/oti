(ns oti.ui.routes
    (:require-macros [secretary.core :refer [defroute]])
    (:require [secretary.core :as secretary]
              [re-frame.core :as re-frame]
              [pushy.core :as pushy]))

(def routes
  [{:view :exam-sessions-panel :url "/oti/virkailija" :text "Koetilaisuudet"}
   {:view :students-panel :url "/oti/virkailija/henkilot" :text "Henkilötiedot"}])

(defn app-routes []
  (secretary/set-config! :prefix "/")

  (doseq [{:keys [view url]} routes]
    (secretary/add-route! url #(re-frame/dispatch [:set-active-panel view])))

  ;; Pushy handles the HTML5 history based navigation
  (-> (pushy/pushy secretary/dispatch!
                   (fn [x]
                     (when (secretary/locate-route x) x)))
      (pushy/start!)))
