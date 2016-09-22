(ns oti.ui.routes
    (:require-macros [secretary.core :refer [defroute]])
    (:require [secretary.core :as secretary]
              [re-frame.core :as re-frame]
              [pushy.core :as pushy]))

(defn app-routes []
  (secretary/set-config! :prefix "/")

  ;; Front-end routes
  (defroute "/oti/virkailija" []
    (re-frame/dispatch [:set-active-panel :exam-sessions-panel]))

  (defroute "/oti/virkailija/students" []
    (re-frame/dispatch [:set-active-panel :students-panel]))

  ;; Pushy handles the HTML5 history based navigation
  (-> (pushy/pushy secretary/dispatch!
                   (fn [x]
                     (when (secretary/locate-route x) x)))
      (pushy/start!)))
