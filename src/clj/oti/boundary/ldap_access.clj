(ns oti.boundary.ldap-access)

(defprotocol LdapAccess
  (user-has-access? [ldap username]))
