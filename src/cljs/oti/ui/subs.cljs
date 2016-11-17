(ns oti.ui.subs
    (:require-macros [reagent.ratom :refer [reaction]])
    (:require [re-frame.core :as re-frame]))

(def interesting-keys [:user :active-panel :flash-message :active-panel-data :loading? :section-and-module-names
                       :accreditation-types :diploma-count :confirmation-dialog])

(doseq [key interesting-keys]
  (re-frame/reg-sub
    key
    (fn [db _]
      (key db))))
