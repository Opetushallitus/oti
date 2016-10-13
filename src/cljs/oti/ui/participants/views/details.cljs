(ns oti.ui.participants.views.details
  (:require [re-frame.core :as re-frame]))

(defn participant-details-panel [participant-id]
  (re-frame/dispatch [:load-participant-details participant-id])
  (let [participant-details (re-frame/subscribe [:participant-details])]
    ))

