(ns oti.ui.app
  (:require [reagent.core :as r]))

(defn app []
  [:h1 "Opetushallinnon tutkintoon ilmoittautuminen"])

(defn ^:export start []
  (r/render [app] (.getElementById js/document "app")))
