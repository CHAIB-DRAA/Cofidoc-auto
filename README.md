# VSL → Cofidoc Auto 🚗

App Android compagnon pour automatiser la facturation Cofidoc depuis VSL-Mobile.

## Ce que ça fait

1. Se connecte à ton backend VSL-Mobile (vsl-taxi.onrender.com)
2. Récupère toutes les courses du jour
3. Ouvre Cofidoc et remplit automatiquement :
   - ✅ Nom du patient
   - ✅ Adresse de départ
   - ✅ Destination
   - ✅ Distance (km)
   - ✅ Date de la course
   - ✅ Péages (si > 0)
   - ⌨️ N° Sécu + Date de naissance → toi (une seule fois par patient)

---

## Installation

### Prérequis
- Android Studio Hedgehog (2023.1.1) ou plus récent
- JDK 17
- Android 8.0+ sur le téléphone

### Étapes

1. **Ouvrir dans Android Studio**
   ```
   File → Open → sélectionner le dossier cofidoc-auto/
   ```

2. **Synchroniser Gradle**
   ```
   File → Sync Project with Gradle Files
   ```

3. **Vérifier le package Cofidoc**
   Connecte ton téléphone et lance :
   ```bash
   adb shell pm list packages | grep cofidoc
   ```
   Si le package n'est pas `fr.cofidoc.mobile`, modifie la constante
   `COFIDOC_PACKAGE` dans `CofidocAutoService.kt`.

4. **Compiler et installer**
   ```
   Run → Run 'app'
   ```

---

## Premier lancement

1. Ouvre l'app **VSL→Cofidoc**
2. Connecte-toi avec tes identifiants VSL-Mobile
3. Un bandeau jaune t'invite à activer le service d'accessibilité
4. Va dans : **Paramètres → Accessibilité → Services installés → VSL→Cofidoc Auto** → Activer
5. Reviens dans l'app

---

## Utilisation quotidienne

1. Ouvre **VSL→Cofidoc** en fin de journée
2. Tes courses du jour s'affichent automatiquement
3. Appuie sur **▶ Démarrer la facturation**
4. L'app ouvre Cofidoc et remplit les champs automatiquement
5. Complète **N° Sécu + Date de naissance**
6. Appuie sur **✅ Course suivante** dans l'app
7. Répète jusqu'à la dernière course

---

## Dépannage

**"Cofidoc non trouvé"**
→ Vérifie le package avec `adb shell pm list packages | grep cofidoc`
→ Mets à jour `COFIDOC_PACKAGE` dans `CofidocAutoService.kt`

**Les champs ne se remplissent pas**
→ Cofidoc a peut-être changé ses IDs de champs
→ Active les options développeur + inspecte avec `uiautomatorviewer`
→ Modifie les hints dans `findEditableFieldByHint()` selon les vrais noms de champs

**Erreur 401 au chargement**
→ Token expiré → déconnecte-toi et reconnecte-toi

---

## Structure du code

```
app/src/main/java/com/vsl/cofidocauto/
├── model/
│   └── Ride.kt              ← Structure des données
├── network/
│   └── VslApi.kt            ← Client API Retrofit
├── service/
│   └── CofidocAutoService.kt ← ⭐ Le robot d'accessibilité
└── ui/
    └── MainActivity.kt      ← Interface utilisateur
```
