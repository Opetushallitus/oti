(ns oti.ui.views.exam-sessions
  (:require [re-frame.core :as re-frame]))

(defn exam-sessions-panel []
  (let [name (re-frame/subscribe [:name])]
    (fn []
      [:div [:h2 "Koetilaisuudet"]])))
