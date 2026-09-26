// Rejoue le jeu de données du dossier réel 2463 PAR L'API, rôle par rôle, depuis jeu-2463.json (section « entree »),
// et consigne ce que le serveur a répondu dans la section « resultat » du même fichier. Aucune écriture directe en base.
//
//   node rejouer-2463.mjs --etape 0        entité et acteurs (vérification de l'entité 11, PRMP LERAVO et son compte)
//   node rejouer-2463.mjs --etape 1        plan de passation 2026 (dossier, PPM, ligne 2463, cinq lots, calendrier) et
//                                          son circuit CNM jusqu'au PV signé favorable
//   node rejouer-2463.mjs --etape 2        projet d'AGPM dérivé du plan → agpm-2026.json + agpm-2026.html
//   node rejouer-2463.mjs --etape 3        fiche DAO : cadrage, blocs B02-B10, besoin B12 (lots 4 et 5 par duplication),
//                                          contrôles, validation
//   node rejouer-2463.mjs --etape 4        téléchargement des documents produits → documents/
//   node rejouer-2463.mjs --etape 5        export complet (resultat.export) + verification.md
//   node rejouer-2463.mjs --etapes 0-5     enchaîne
//
// Options : --api http://localhost:8080 (défaut) · --mdp <mot de passe commun des comptes> (défaut : PRS_MDP, sinon Test@1234)
//           · --jeu <fichier> (défaut : jeu-2463.json à côté du script)
//
// ⚠️ Écrit en base de développement. Une valeur qui manque n'est jamais inventée : le script s'arrête (code 2) et la nomme.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ICI = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const opt = (nom, defaut) => (args.includes(nom) ? args[args.indexOf(nom) + 1] : defaut);
const API = opt('--api', 'http://localhost:8080');
const MDP = opt('--mdp', process.env.PRS_MDP || 'Test@1234');
const FICHIER_JEU = path.resolve(ICI, opt('--jeu', 'jeu-2463.json'));
const plage = opt('--etapes', opt('--etape', null));
if (plage == null) {
  console.error('usage : node rejouer-2463.mjs --etape <0..5> | --etapes <a-b> [--api …] [--mdp …] [--jeu …]');
  process.exit(1);
}
const [DE, A] = String(plage).split('-').map(Number);
const FIN = A === undefined || Number.isNaN(A) ? DE : A;
const ETAPES = Array.from({ length: FIN - DE + 1 }, (_, i) => DE + i);

const jeu = JSON.parse(fs.readFileSync(FICHIER_JEU, 'utf8'));
jeu.resultat ??= {};
const E = jeu.entree;
const R = jeu.resultat;
const sauver = () => fs.writeFileSync(FICHIER_JEU, JSON.stringify(jeu, null, 2) + '\n', 'utf8');
const AUJOURDHUI = new Date().toISOString().slice(0, 10);

// ── Sessions par compte (cookie + jeton CSRF), comme le navigateur ──────────────────────────────────────────────
const sessions = new Map();
const ouvrir = async (login) => {
  if (sessions.has(login)) return sessions.get(login);
  const s = { cookie: '', xsrf: '' };
  s.maj = (r) => {
    for (const c of r.headers.getSetCookie?.() ?? []) {
      const kv = c.split(';')[0];
      const k = kv.slice(0, kv.indexOf('='));
      const v = kv.slice(kv.indexOf('=') + 1);
      s.cookie = [...s.cookie.split('; ').filter((x) => x && !x.startsWith(k + '=')), k + '=' + v].join('; ');
      if (k === 'XSRF-TOKEN') s.xsrf = decodeURIComponent(v);
    }
  };
  const r = await fetch(API + '/api/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ login, motDePasse: MDP }),
  });
  s.maj(r);
  if (!r.ok) throw new Error('connexion ' + login + ' refusée : ' + r.status);
  const corpsLogin = await r.json().catch(() => null);
  s.moi = corpsLogin && typeof corpsLogin === 'object'
    ? Object.fromEntries(Object.entries(corpsLogin).filter(([k]) => !/token|jeton|mot/i.test(k))) : null;
  // ⚠️ Le jeton CSRF n'arrive pas avec le login : une première lecture le pose (comme le chargement de la page).
  const amorce = await fetch(API + '/api/auth/entites', { headers: { Cookie: s.cookie } });
  s.maj(amorce);
  sessions.set(login, s);
  return s;
};
const appel = async (login, methode, url, corps) => {
  const s = await ouvrir(login);
  const entetes = { Cookie: s.cookie };
  if (methode !== 'GET') {
    entetes['X-XSRF-TOKEN'] = s.xsrf;
    if (corps !== undefined) entetes['Content-Type'] = 'application/json';
  }
  const r = await fetch(API + url, { method: methode, headers: entetes, body: corps !== undefined ? JSON.stringify(corps) : undefined });
  s.maj(r);
  const texte = await r.text();
  let json = null;
  try { json = texte ? JSON.parse(texte) : null; } catch { json = null; }
  return { ok: r.ok, statut: r.status, corps: json, texte: texte.slice(0, 400) };
};
const octets = async (login, url) => {
  const s = await ouvrir(login);
  const r = await fetch(API + url, { headers: { Cookie: s.cookie } });
  if (!r.ok) throw new Error(url + ' : ' + r.status);
  return Buffer.from(await r.arrayBuffer());
};
const liste = (c) => (Array.isArray(c) ? c : (c?.content ?? []));
const echec = (quoi, r) => {
  sauver();
  console.error('  ✗ ' + quoi + ' — ' + (r?.statut ?? '') + ' ' + (r?.corps ? JSON.stringify(r.corps).slice(0, 600) : r?.texte ?? ''));
  process.exit(1);
};
const arret = (motif) => {
  sauver();
  console.error('  ■ ARRÊT (valeur manquante ou état inattendu) : ' + motif);
  process.exit(2);
};
const ok = (m) => console.log('  ✓ ' + m);
const info = (m) => console.log('    ' + m);
const titre = (n, t) => console.log('\n══ ÉTAPE ' + n + ' — ' + t + ' ══');
const PRMP = () => E.prmp.login;

// ── Étape 0 — entité et acteurs ──────────────────────────────────────────────────────────────────────────────────
async function etape0() {
  titre(0, "Entité et acteurs");
  const r0 = { date: AUJOURDHUI };
  const ent = await appel(E.comptes.admin, 'GET', '/api/entite-contracts/' + E.entite.id);
  if (!ent.ok) echec("lecture de l'entité " + E.entite.id, ent);
  const normal = (s) => String(s ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/\s+/g, ' ').trim().toUpperCase();
  r0.entite = {
    idEntiteContract: ent.corps.idEntiteContract, libelleEntite: ent.corps.libelleEntite, adresse: ent.corps.adresse,
    categorieEntite: ent.corps.categorieEntite, idLocalite: ent.corps.idLocalite,
    libelleConforme: normal(ent.corps.libelleEntite) === normal(E.entite.attendu.libelleEntite),
    adresseConforme: normal(ent.corps.adresse).includes('PORTE 204') && normal(ent.corps.adresse).includes('FIADANANA'),
    sigleStockable: false,
  };
  ok('entité ' + ent.corps.idEntiteContract + ' · « ' + ent.corps.libelleEntite + ' » · libellé ' + (r0.entite.libelleConforme ? 'conforme (majuscules sans accents)' : 'DIFFÉRENT')
    + ' · adresse ' + (r0.entite.adresseConforme ? 'conforme' : 'DIFFÉRENTE'));
  info("pas de champ sigle sur l'entité : « MESupReS » n'est pas stockable");

  // PRMP : créée avec son compte si absente.
  const p = E.prmp;
  const existe = await appel(E.comptes.admin, 'GET', '/api/prmps/' + p.idPrmp);
  if (existe.ok) {
    ok('PRMP ' + p.idPrmp + ' déjà présente (' + existe.corps.nomPrmp + ' ' + existe.corps.prenomsPrmp + ')');
    r0.prmp = { ...existe.corps, creee: false };
  } else {
    const corps = {
      idPrmp: p.idPrmp, nomPrmp: p.nomPrmp, prenomsPrmp: p.prenomsPrmp, arreteNomin: p.arreteNomin, dateNomin: p.dateNomin,
      cin: p.cin, dateCin: p.dateCin, lieuCin: p.lieuCin, emailPrmp: p.emailPrmp, telPrmp: p.telPrmp, login: p.login, motDePasse: MDP,
    };
    const cr = await appel(E.comptes.admin, 'POST', '/api/prmps', corps);
    if (!cr.ok) echec('création de la PRMP', cr);
    r0.prmp = { ...cr.corps, creee: true, login: p.login };
    ok('PRMP ' + cr.corps.idPrmp + ' créée : ' + cr.corps.nomPrmp + ' ' + cr.corps.prenomsPrmp + ' · compte ' + p.login);
  }

  // Rattachement : une seule PRMP active par entité — le lien d'une autre PRMP est désactivé, le sien créé.
  const liens = liste((await appel(E.comptes.admin, 'GET', '/api/prmp-entites')).corps);
  r0.liensDesactives = [];
  for (const l of liens.filter((x) => x.idEntiteContract === E.entite.id && x.actif && x.idPrmp !== p.idPrmp)) {
    const d = await appel(E.comptes.admin, 'PUT', '/api/prmp-entites/' + l.idPrmpEntite, { ...l, actif: false });
    if (!d.ok) echec('désactivation du lien ' + l.idPrmpEntite + ' (' + l.idPrmp + ')', d);
    r0.liensDesactives.push({ idPrmpEntite: l.idPrmpEntite, idPrmp: l.idPrmp });
    ok('lien ' + l.idPrmpEntite + ' (' + l.idPrmp + ' ↔ entité ' + E.entite.id + ') désactivé');
  }
  const mien = liens.find((x) => x.idEntiteContract === E.entite.id && x.idPrmp === p.idPrmp);
  if (mien?.actif) {
    r0.lien = mien;
    ok('rattachement déjà actif (lien ' + mien.idPrmpEntite + ')');
  } else if (mien) {
    const a = await appel(E.comptes.admin, 'PUT', '/api/prmp-entites/' + mien.idPrmpEntite, { ...mien, actif: true });
    if (!a.ok) echec('activation du lien', a);
    r0.lien = a.corps;
    ok('rattachement activé (lien ' + a.corps.idPrmpEntite + ')');
  } else {
    const a = await appel(E.comptes.admin, 'POST', '/api/prmp-entites', { idPrmp: p.idPrmp, idEntiteContract: E.entite.id, actif: true });
    if (!a.ok) echec('rattachement de la PRMP à l’entité', a);
    r0.lien = a.corps;
    ok('rattachement créé et actif (lien ' + a.corps.idPrmpEntite + ')');
  }

  const moi = await ouvrir(p.login);
  r0.compte = { login: p.login, moi: moi.moi };
  ok('connexion ' + p.login + ' : ' + JSON.stringify(moi.moi).slice(0, 160));
  const entites = liste((await appel(p.login, 'GET', '/api/entite-contracts')).corps);
  r0.entiteVisible = entites.some((e) => e.idEntiteContract === E.entite.id);
  info("l'entité 11 est " + (r0.entiteVisible ? '' : 'NON ') + 'dans le référentiel lu par la PRMP');
  R.etape0 = r0;
  sauver();
}

// ── Étape 1 — plan de passation et circuit CNM ──────────────────────────────────────────────────────────────────
async function etape1() {
  titre(1, 'Plan de passation 2026 et circuit CNM');
  const r1 = R.etape1 ?? { date: AUJOURDHUI };
  R.etape1 = r1;
  const prmp = PRMP();
  if (r1.idDossier == null) {
    const ligne = {
      ...E.plan.ligne,
      lots: E.plan.lots.map((l) => ({ designationLot: l.designationLot, montLot: l.montLot })),
      processus: E.plan.processus,
    };
    const r = await appel(prmp, 'POST', '/api/saisies/ppm', {
      idEntiteContract: E.entite.id, exercice: E.plan.exercice, dateSignature: E.plan.dateSignature, marches: [ligne],
    });
    if (!r.ok) echec('saisie du PPM', r);
    r1.idDossier = r.corps.idDossier;
    r1.creation = { statut: r.corps.statut, refeDossier: r.corps.refeDossier, idSousType: r.corps.idSousType };
    ok('dossier ' + r1.idDossier + ' · ' + r.corps.statut + ' · ' + (r.corps.refeDossier ?? '—') + ' · sous-type ' + (r.corps.idSousType ?? '—'));
  }
  const marches = liste((await appel(prmp, 'GET', '/api/marches?dossier=' + r1.idDossier)).corps);
  const m = marches[0];
  if (!m) arret('aucune ligne de marché lue sur le dossier ' + r1.idDossier);
  r1.idDetail = m.idDetail;
  r1.idPpm = m.idPpm;
  r1.ligne = m;
  r1.lots = liste((await appel(prmp, 'GET', '/api/lots/par-marche/' + m.idDetail)).corps);
  r1.previsions = liste((await appel(prmp, 'GET', '/api/marche-previsions?marche=' + m.idDetail)).corps);
  ok('ligne ' + m.idDetail + ' · ' + r1.lots.length + ' lots · ' + r1.previsions.length + ' étapes prévisionnelles · '
    + Number(m.montEstim).toLocaleString('fr-FR') + ' Ar · forme ' + m.formeMarche + ' · mode ' + m.idMode + ' · nature ' + m.idNature);
  if (r1.lots.length !== E.plan.lots.length) arret(r1.lots.length + ' lots au plan au lieu de ' + E.plan.lots.length);
  if (r1.previsions.length !== E.plan.processus.length) arret(r1.previsions.length + ' prévisions au lieu de ' + E.plan.processus.length);

  // Soumission
  const d0 = (await appel(prmp, 'GET', '/api/dossiers/' + r1.idDossier)).corps;
  if (d0?.statut === 'BROUILLON') {
    const s = await appel(prmp, 'POST', '/api/dossiers/' + r1.idDossier + '/soumettre', {});
    if (!s.ok) echec('soumission du plan', s);
    ok('soumis · ' + s.corps.statut + ' · ' + s.corps.refeDossier);
  }
  // Réception (Secrétaire)
  const ex = await appel(E.comptes.secretaire, 'GET', '/api/receptions/dossier/' + r1.idDossier + '/existe');
  if (ex.corps?.existe) {
    r1.idReception = ex.corps.idReception;
  } else if (r1.idReception == null) {
    const rc = await appel(E.comptes.secretaire, 'POST', '/api/receptions', {
      idDossier: r1.idDossier, numPassage: 1, typePassage: 'INITIAL', imCtrlRecept: E.comptes.secretaire,
      dateReception: E.circuit.dateActes, complet: true, observation: E.circuit.reception.observation,
    });
    if (!rc.ok) echec('réception', rc);
    r1.idReception = rc.corps.idReception;
    r1.reception = rc.corps;
  }
  ok('réception ' + r1.idReception + ' (' + E.comptes.secretaire + ')');
  // Dispatch (Président → Membre)
  if (r1.idDispatch == null) {
    const dp = await appel(E.comptes.president, 'POST', '/api/dispatchs', {
      idReception: r1.idReception, imCtrlMembre: E.comptes.membre, dateDispatch: E.circuit.dateActes,
      instructions: E.circuit.dispatch.instructions, interimDispatch: false,
    });
    if (!dp.ok) echec('dispatch', dp);
    r1.idDispatch = dp.corps.idDispatch;
    r1.dispatch = dp.corps;
  }
  ok('dispatch ' + r1.idDispatch + ' → ' + E.comptes.membre);
  // Examen (Membre) : clé assignée par le client, grille complète conforme, avis favorable
  if (r1.idExamen == null) {
    const tous = liste((await appel(E.comptes.president, 'GET', '/api/examens')).corps);
    const idLibre = Math.max(0, ...tous.map((e) => e.idExamen)) + 1;
    const cr = await appel(E.comptes.membre, 'POST', '/api/examens', {
      idExamen: idLibre, idDispatch: r1.idDispatch, imCtrlMembre: E.comptes.membre, dateExamen: E.circuit.dateActes,
    });
    if (!cr.ok) echec("ouverture de l'examen", cr);
    r1.idExamen = cr.corps?.idExamen ?? idLibre;
  }
  ok('examen ' + r1.idExamen);
  if (r1.idPv == null) {
    const grille = liste((await appel(E.comptes.membre, 'GET', '/api/points-ctrls?sousType=PPM-AGPM')).corps).filter((p) => p.portee !== 'SUPPRESSION');
    const deja = liste((await appel(E.comptes.membre, 'GET', '/api/examen-details?examen=' + r1.idExamen)).corps);
    let id = Math.max(0, ...deja.map((d) => d.idDetailExamen));
    r1.grille = [];
    for (const pt of grille) {
      if (deja.some((d) => d.idPtControle === pt.idPointCtrl)) continue;
      const r = await appel(E.comptes.membre, 'POST', '/api/examen-details', {
        idDetailExamen: ++id, idExamen: r1.idExamen, idDetail: pt.portee === 'LIGNE' ? r1.idDetail : null, idPtControle: pt.idPointCtrl, conforme: true,
      });
      r1.grille.push({ idPointCtrl: pt.idPointCtrl, libelle: pt.libelPointCtrl, portee: pt.portee, statut: r.statut });
      if (!r.ok) info('point ' + pt.idPointCtrl + ' (' + pt.libelPointCtrl + ') : ' + r.statut + ' ' + String(r.corps?.message ?? '').slice(0, 100));
    }
    ok('grille : ' + r1.grille.filter((g) => g.statut === 201 || g.statut === 200).length + '/' + grille.length + ' points conformes posés');
    const so = await appel(E.comptes.membre, 'POST', '/api/examens/' + r1.idExamen + '/soumettre', { idAvis: E.circuit.examen.avis });
    if (!so.ok) echec("soumission de l'examen", so);
    r1.idPv = so.corps?.idPv;
    r1.examenSoumis = { statutPv: so.corps?.statutPv, idAvis: so.corps?.idAvis };
  }
  ok('projet de PV ' + r1.idPv);
  // PV : soumission du projet (Membre), visa (Président, co-signataire), signature du co-signataire
  let pv = (await appel(E.comptes.president, 'GET', '/api/pv-examens/' + r1.idPv)).corps;
  if (pv?.statutPv === 'BROUILLON' || pv?.statutPv === 'EN_RECTIFICATION') {
    const so = await appel(E.comptes.membre, 'POST', '/api/pv-examens/' + r1.idPv + '/soumettre', { imActeur: E.comptes.membre });
    if (!so.ok) echec('soumission du projet de PV', so);
    pv = so.corps;
  }
  if (pv?.statutPv === 'PROJET_SOUMIS') {
    const v = await appel(E.comptes.president, 'POST', '/api/pv-examens/' + r1.idPv + '/viser', {
      imActeur: E.comptes.president, coSignataires: [E.comptes.coSignataire], commentaire: E.circuit.visa.commentaire,
    });
    if (!v.ok) echec('visa du PV', v);
    pv = v.corps;
    ok('visé · ' + pv.statutPv + ' · co-signataire ' + pv.imMembreCoSignataire);
  }
  if (pv?.statutPv !== 'SIGNE') {
    const designe = pv?.imMembreCoSignataire ?? E.comptes.coSignataire;
    const sg = await appel(designe, 'POST', '/api/pv-examens/' + r1.idPv + '/signer', { imActeur: designe, role: 'MEMBRE' });
    if (!sg.ok) echec('signature du Membre désigné (' + designe + ')', sg);
    pv = sg.corps;
  }
  r1.pv = { idPv: pv.idPv, refePv: pv.refePv, statutPv: pv.statutPv, idAvis: pv.idAvis, imMembreCoSignataire: pv.imMembreCoSignataire };
  ok('PV ' + (pv.refePv ?? pv.idPv) + ' · ' + pv.statutPv + ' · avis ' + pv.idAvis);
  const d1 = (await appel(prmp, 'GET', '/api/dossiers/' + r1.idDossier)).corps;
  r1.dossier = d1;
  r1.ppm = (await appel(prmp, 'GET', '/api/ppms/' + r1.idPpm)).corps;
  const el = liste((await appel(prmp, 'GET', '/api/dmcs/eligibles')).corps).find((x) => x.idDetail === r1.idDetail);
  r1.eligible = el ?? null;
  ok('dossier ' + d1.refeDossier + ' · ' + d1.statut + ' · sous-type ' + d1.idSousType + ' · PPM ' + r1.ppm?.reference + ' (mise à jour n° ' + (r1.ppm?.numMaj ?? 0) + ')');
  if (!el) arret("la ligne " + r1.idDetail + " n'est pas servie par /api/dmcs/eligibles");
  ok('ligne éligible au DAO : ' + el.categorie + ' / ' + el.formeMarche + ' · ' + el.libelleMode);
  sauver();
}

// ── Étape 2 — projet d'AGPM dérivé du plan ──────────────────────────────────────────────────────────────────────
const montantFr = (v) => (v == null ? '—' : Math.round(Number(v)).toLocaleString('fr-FR').replace(/ | /g, ' '));
const dateFr = (iso) => (iso ? iso.slice(8, 10) + '/' + iso.slice(5, 7) + '/' + iso.slice(0, 4) : '—');
const html = (s) => String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

async function etape2() {
  titre(2, "Projet d'AGPM 2026 dérivé du plan");
  const r1 = R.etape1 ?? arret("l'étape 1 n'a pas été jouée");
  const prmp = PRMP();
  const ppm = (await appel(prmp, 'GET', '/api/ppms/' + r1.idPpm)).corps;
  const dossier = (await appel(prmp, 'GET', '/api/dossiers/' + r1.idDossier)).corps;
  const marches = liste((await appel(prmp, 'GET', '/api/marches?dossier=' + r1.idDossier)).corps).filter((m) => !m.supprimee);
  const modes = liste((await appel(prmp, 'GET', '/api/mode-passations')).corps);
  const natures = liste((await appel(prmp, 'GET', '/api/natures')).corps);
  const capm = liste((await appel(prmp, 'GET', '/api/capm')).corps);
  const modeParId = new Map(modes.map((x) => [x.idMode, x]));
  const natureParId = new Map(natures.map((x) => [x.idNature, x]));
  const capmLibelle = new Map(capm.map((c) => [c.idCapm, String(c.libelleProcessus ?? '').toUpperCase()]));
  const lignes = [];
  for (const m of marches) {
    const mode = modeParId.get(m.idMode);
    if (!mode?.declencheAgpm) continue;
    const prev = liste((await appel(prmp, 'GET', '/api/marche-previsions?marche=' + m.idDetail)).corps);
    const lancement = prev.find((p) => (capmLibelle.get(p.idCapm) ?? '').includes('LANCEMENT'));
    lignes.push({
      idDetail: m.idDetail, compte: m.numCompte ?? '', nature: natureParId.get(m.idNature)?.libelle ?? '', objet: m.designationMarche ?? '',
      montant: m.montEstim, financement: m.financement ?? '', modeLibelle: mode.libelle, dateDao: lancement?.dateDebut ?? null,
      etapeLancement: lancement ? { idCapm: lancement.idCapm, libelle: capm.find((c) => c.idCapm === lancement.idCapm)?.libelleProcessus } : null,
    });
  }
  const entete = {
    exercice: ppm.exercice, entite: R.etape0?.entite?.libelleEntite ?? dossier.libelleEntite ?? '', signataire: ppm.signataire,
    dateInitiale: ppm.datePpmInit ?? ppm.dateSignature, numMajPrec: ppm.numMajPrec ?? 0, dateMajPrec: ppm.dateMajPrec ?? null, numMaj: ppm.numMaj ?? 0,
  };
  const agpm = {
    _: "Projet d'AGPM dérivé du plan, tel que le front le calcule (shared/prmp/agpm.ts : marchés dont le mode déclenche l'AGPM ; date du DAO = début de "
      + "l'étape prévisionnelle « lancement »). Le serveur n'a pas de rendu : il sert agpmRequis, le sous-type PPM-AGPM et la grille d'examen (points 8, 12, 13, 14).",
    lu: AUJOURDHUI,
    sources: ['/api/ppms/' + r1.idPpm, '/api/dossiers/' + r1.idDossier, '/api/marches?dossier=' + r1.idDossier, '/api/marche-previsions?marche=…', '/api/mode-passations', '/api/natures', '/api/capm'],
    serveur: { agpmRequis: ppm.agpmRequis ?? null, idSousType: dossier.idSousType, refeDossier: dossier.refeDossier, referencePpm: ppm.reference },
    entete,
    lignes,
  };
  const l = lignes.find((x) => x.idDetail === r1.idDetail);
  agpm.controle = {
    ligne2463Presente: !!l,
    objet: l?.objet === E.plan.ligne.designationMarche, mode: l?.modeLibelle, montant: l?.montant, dateDao: l?.dateDao,
    dateDaoAttendue: E.plan.processus.find((p) => p.idCapm === 111)?.dateDebut,
  };
  fs.writeFileSync(path.join(ICI, 'agpm-2026.json'), JSON.stringify(agpm, null, 2) + '\n', 'utf8');
  const rangs = lignes.map((x) => '<tr><td>' + html(x.compte || '—') + '</td><td>' + html(x.nature || '—') + '</td><td class="objet">' + html(x.objet)
    + '</td><td class="num">' + html(montantFr(x.montant)) + '</td><td>' + html(x.financement || '—') + '</td><td>' + html(x.modeLibelle) + '</td><td>' + dateFr(x.dateDao) + '</td></tr>').join('\n');
  const page = `<!doctype html>
<html lang="fr"><head><meta charset="utf-8"><title>AGPM 2026 — MESupReS (reconstitution)</title>
<style>
body{font-family:Arial,Helvetica,sans-serif;font-size:12px;color:#000;margin:24px;background:#fff}
.bandeau{border:1px solid #999;background:#f4f4f4;padding:8px 10px;margin-bottom:18px;font-size:11px}
h3{text-align:center;font-size:14px;margin:0 0 14px;text-transform:uppercase}
.entete{display:flex;justify-content:space-between;gap:24px;margin-bottom:12px}.entete p{margin:2px 0}
table{border-collapse:collapse;width:100%}th,td{border:1px solid #000;padding:4px 6px;vertical-align:top}
th{background:#e6e6e6;font-weight:bold;text-align:center}td.num{text-align:right;white-space:nowrap}td.objet{width:31%}
.note{margin-top:8px;font-style:italic}
</style></head><body>
<div class="bandeau">Reconstitution hors application — données servies par l'API PRS20 le ${AUJOURDHUI} (${agpm.sources.join(', ')}). Le rendu applicatif est le composant <code>agpm-doc.ts</code> du front ; le serveur ne produit pas ce document (sous-type servi : ${html(dossier.idSousType)}, agpmRequis : ${html(String(ppm.agpmRequis))}).</div>
<h3>Avis général de passation des marchés pour l'année ${html(entete.exercice)}</h3>
<div class="entete"><div><p><u>Autorité Contractante</u> : <strong>${html(entete.entite)}</strong></p><p><u>Nom de la PRMP</u> : <strong>${html(entete.signataire)}</strong></p></div>
<div><p><u>Date d'établissement du Document initial</u> : ${dateFr(entete.dateInitiale)}</p><p><u>Numéro et date de la dernière mise à jour</u> : ${html(entete.numMajPrec)}${entete.dateMajPrec ? ' - ' + dateFr(entete.dateMajPrec) : ''}</p><p><u>Numéro de la présente mise à jour</u> : ${html(entete.numMaj)}</p></div></div>
<table><colgroup><col style="width:9%"><col style="width:12%"><col style="width:31%"><col style="width:14%"><col style="width:10%"><col style="width:14%"><col style="width:10%"></colgroup>
<thead><tr><th>COMPTE</th><th>NATURE</th><th>OBJET</th><th>MONTANT ESTIMATIF du MARCHE</th><th>FINANCEMENT</th><th>MODE DE PASSATION</th><th>DATE du DAO</th></tr></thead>
<tbody>
${rangs}
</tbody></table>
<p class="note">Date du DAO = date prévisionnelle de lancement du marché.</p>
</body></html>
`;
  fs.writeFileSync(path.join(ICI, 'agpm-2026.html'), page, 'utf8');
  R.etape2 = { date: AUJOURDHUI, serveur: agpm.serveur, controle: agpm.controle, nbLignes: lignes.length, fichiers: ['agpm-2026.json', 'agpm-2026.html'] };
  ok('projet d’AGPM : ' + lignes.length + ' ligne(s) · sous-type ' + dossier.idSousType + ' · agpmRequis ' + ppm.agpmRequis);
  if (l) ok('ligne 2463 : ' + l.compte + ' · ' + l.nature + ' · ' + montantFr(l.montant) + ' Ar · ' + l.modeLibelle + ' · date du DAO ' + dateFr(l.dateDao));
  else arret("la ligne 2463 n'est pas dans le projet d'AGPM");
  sauver();
}

// ── Étape 3 — la fiche DAO ──────────────────────────────────────────────────────────────────────────────────────
const conditionTenue = (condition, cadrage) => {
  if (!condition) return true;
  const un = (t) => {
    const neg = t.includes('!=');
    const [cle, val] = t.split(neg ? '!=' : '=').map((x) => x.trim());
    const v = String(cadrage[cle] ?? '');
    return neg ? v !== val : v === val;
  };
  if (condition.includes(' ou ')) return condition.split(' ou ').some(un);
  if (condition.includes(' et ')) return condition.split(' et ').every(un);
  return un(condition);
};
const nu = (a) => ({ designation: a.designation, unite: a.unite, quantiteMin: a.quantiteMin ?? null, quantiteMax: a.quantiteMax ?? null, quantite: a.quantite ?? null,
  caracteristiques: (a.caracteristiques ?? []).map((c) => ({ libelle: c.libelle, exigence: c.exigence })) });

async function etape3() {
  titre(3, 'Fiche DAO : cadrage, blocs, besoin, contrôles, validation');
  const r1 = R.etape1 ?? arret("l'étape 1 n'a pas été jouée");
  const prmp = PRMP();
  const r3 = R.etape3 ?? { date: AUJOURDHUI };
  R.etape3 = r3;
  if (r3.idDmc == null) {
    let dmc = await appel(prmp, 'GET', '/api/dmcs/par-marche/' + r1.idDetail);
    if (!dmc.ok) {
      dmc = await appel(prmp, 'POST', '/api/dmcs/par-marche/' + r1.idDetail);
      if (!dmc.ok) echec('création du DMC', dmc);
    }
    r3.idDmc = dmc.corps.idDmc;
    r3.dmc = dmc.corps;
  }
  ok('DMC ' + r3.idDmc + ' · ' + r3.dmc?.reference + ' · ' + r3.dmc?.typeDmcCode);
  let fiche = (await appel(prmp, 'GET', '/api/fiches-marche/' + r3.idDmc)).corps;
  r3.importees = { nombre: Object.keys(fiche.valeursPpm ?? {}).length, cles: Object.keys(fiche.valeursPpm ?? {}) };
  ok(r3.importees.nombre + ' informations importées du plan (valeursPpm) · type ' + fiche.typeMarche + ' · catégorie ' + fiche.categorie);
  if (fiche.statut === 'VALIDEE') {
    ok('fiche déjà validée (version ' + fiche.version + ') : rien à rejouer');
    return;
  }
  const ref = (await appel(prmp, 'GET', '/api/champs-fiche-marche?typeMarche=' + fiche.typeMarche + '&categorie=' + fiche.categorie)).corps;

  const rc = await appel(prmp, 'PUT', '/api/fiches-marche/' + r3.idDmc + '/cadrage', { cadrage: E.fiche.cadrage });
  if (!rc.ok) echec('cadrage', rc);
  fiche = rc.corps;
  ok('cadrage posé · saisieParLot ' + fiche.saisieParLot + ' · nbLots ' + fiche.nbLots);
  if (fiche.nbLots !== E.plan.lots.length) arret('nbLots servi = ' + fiche.nbLots);
  const lots = Array.from({ length: fiche.nbLots }, (_, i) => i + 1);
  const cles = (c) => (c.parLot ? lots : [null]).map((lot) => ({ lot, cle: lot == null ? c.code : c.code + '#' + lot }));
  const valeurDe = (c, lot) => {
    const duJeu = c.parLot && lot != null ? E.fiche.parLot[c.code]?.[lot] : E.fiche.valeurs[c.code];
    if (duJeu !== undefined) return duJeu;
    const deLaFiche = fiche.valeurs?.[lot == null ? c.code : c.code + '#' + lot];   // défaut du référentiel déjà posé
    return deLaFiche == null || deLaFiche === '' ? undefined : deLaFiche;
  };
  const aSaisir = ref.champs.filter((c) => c.source === 'SAISIE' && c.type !== 'PIECE' && conditionTenue(c.condition, E.fiche.cadrage));
  const manquants = aSaisir.filter((c) => c.obligatoire && cles(c).some(({ lot }) => valeurDe(c, lot) === undefined));
  if (manquants.length) {
    manquants.forEach((c) => console.error('      ' + c.code + ' [' + c.type + '] ' + c.libelle));
    arret(manquants.length + ' information(s) obligatoire(s) sans valeur dans la fiche des faits');
  }
  const inutiles = Object.keys(E.fiche.valeurs).filter((code) => !aSaisir.some((c) => c.code === code));
  r3.valeursSansChamp = inutiles;
  if (inutiles.length) info('⚠️ ' + inutiles.length + ' valeur(s) sans champ ouvert : ' + inutiles.join(' '));
  r3.blocs = {};
  for (const bloc of ref.blocs) {
    const champs = aSaisir.filter((c) => c.bloc === bloc.code && cles(c).some(({ lot }) => valeurDe(c, lot) !== undefined));
    if (!champs.length) continue;
    const corps = {};
    for (const c of champs) for (const { lot, cle } of cles(c)) {
      const v = valeurDe(c, lot);
      if (v !== undefined) corps[cle] = String(v);
    }
    const r = await appel(prmp, 'PUT', '/api/fiches-marche/' + r3.idDmc + '/blocs/' + bloc.code, { valeurs: corps });
    if (!r.ok) echec('bloc ' + bloc.code, r);
    fiche = r.corps;
    r3.blocs[bloc.code] = Object.keys(corps).length;
    ok(bloc.code + ' — ' + Object.keys(corps).length + ' information(s)');
  }
  // Besoin : lots 1 à 3 depuis la fiche des faits, lots 4 et 5 par relecture des lots 2 et 3 (le geste « Dupliquer »).
  for (const lot of [...new Set(E.fiche.articles.map((a) => a.lot))].sort()) {
    const duLot = E.fiche.articles.filter((a) => a.lot === lot);
    const r = await appel(prmp, 'PUT', '/api/fiches-marche/' + r3.idDmc + '/articles?lot=' + lot, { articles: duLot });
    if (!r.ok) echec('besoin du lot ' + lot, r);
    ok('besoin du lot ' + lot + ' : ' + duLot.length + ' article(s)');
  }
  r3.duplications = [];
  for (const [cible, source] of Object.entries(E.fiche.duplications)) {
    const tous = liste((await appel(prmp, 'GET', '/api/fiches-marche/' + r3.idDmc + '/articles')).corps);
    const modele = tous.filter((a) => a.lot === Number(source)).sort((a, b) => a.ordre - b.ordre).map((a) => ({ lot: Number(cible), ...nu(a) }));
    const r = await appel(prmp, 'PUT', '/api/fiches-marche/' + r3.idDmc + '/articles?lot=' + cible, { articles: modele });
    if (!r.ok) echec('duplication du lot ' + source + ' vers le lot ' + cible, r);
    r3.duplications.push({ cible: Number(cible), source: Number(source), articles: modele.length });
    ok('lot ' + cible + ' dupliqué depuis le lot ' + source + ' (' + modele.length + ' articles)');
  }
  const articles = liste((await appel(prmp, 'GET', '/api/fiches-marche/' + r3.idDmc + '/articles')).corps);
  r3.identiques = {};
  for (const [cible, source] of Object.entries(E.fiche.duplications)) {
    const a = JSON.stringify(articles.filter((x) => x.lot === Number(cible)).sort((x, y) => x.ordre - y.ordre).map(nu));
    const b = JSON.stringify(articles.filter((x) => x.lot === Number(source)).sort((x, y) => x.ordre - y.ordre).map(nu));
    r3.identiques['lot' + cible + '=lot' + source] = a === b;
    ok('lot ' + cible + ' ≡ lot ' + source + ' : ' + (a === b ? 'identiques' : 'DIFFÉRENTS'));
    if (a !== b) arret('les lots dupliqués diffèrent');
  }
  r3.articles = { total: articles.length, parLot: Object.fromEntries(lots.map((l) => [l, articles.filter((a) => a.lot === l).length])),
    caracteristiques: articles.reduce((n, a) => n + (a.caracteristiques?.length ?? 0), 0) };
  // Contrôles
  const ctl = await appel(prmp, 'POST', '/api/fiches-marche/' + r3.idDmc + '/controler');
  if (!ctl.ok) echec('contrôles', ctl);
  const bilan = ctl.corps.bilanControles ?? ctl.corps;
  const bloquants = bilan.bloquants ?? [];
  const avertissements = bilan.avertissements ?? [];
  const oks = bilan.ok ?? [];
  const regles = (l) => [...new Set(l.map((x) => x.regle))];
  r3.bilan = { bloquants, avertissements, ok: oks.map((x) => ({ regle: x.regle, champs: x.champs, lot: x.lot ?? null, message: x.message })) };
  ok('bilan : ' + bloquants.length + ' bloquant(s) [' + regles(bloquants).join(', ') + '] · ' + avertissements.length + ' avertissement(s) [' + regles(avertissements).join(', ') + '] · ' + oks.length + ' ok [' + regles(oks).join(', ') + ']');
  const attendus = ['DATES_ORDRE', 'QUANTITES_ORDRE', 'GARANTIE_TAUX', 'AVANCE_SUP_5_GARANTIE'];
  r3.controlesAttendus = Object.fromEntries(attendus.map((rg) => [rg, {
    bloquant: bloquants.filter((x) => x.regle === rg).length, avertissement: avertissements.filter((x) => x.regle === rg).length, ok: oks.filter((x) => x.regle === rg).length }]));
  info(JSON.stringify(r3.controlesAttendus));
  if (bloquants.length) { bloquants.forEach((b) => info('! ' + b.regle + ' ' + (b.champs ?? []).join(',') + ' ' + b.message)); arret('la fiche porte des bloquants'); }
  // Validation
  const v = await appel(prmp, 'POST', '/api/fiches-marche/' + r3.idDmc + '/valider');
  if (!v.ok) echec('validation', v);
  r3.validation = { version: v.corps.version, statut: v.corps.statut, dateValidation: v.corps.dateValidation ?? null, nbValeurs: Object.keys(v.corps.valeurs ?? {}).length };
  ok('version ' + v.corps.version + ' ' + v.corps.statut + ' · ' + r3.validation.nbValeurs + ' valeurs figées');
  sauver();
}

// ── Étape 4 — documents produits ────────────────────────────────────────────────────────────────────────────────
async function etape4() {
  titre(4, 'Documents produits à la validation');
  const r3 = R.etape3 ?? arret("l'étape 3 n'a pas été jouée");
  const prmp = PRMP();
  const docs = liste((await appel(prmp, 'GET', '/api/fiches-marche/' + r3.idDmc + '/documents')).corps);
  const dossier = path.join(ICI, 'documents');
  fs.mkdirSync(dossier, { recursive: true });
  const r4 = { date: AUJOURDHUI, nombre: docs.length, parType: {}, fichiers: [] };
  for (const d of docs) {
    const contenu = await octets(prmp, '/api/fiches-marche/documents/' + d.idDocument + '/contenu');
    fs.writeFileSync(path.join(dossier, d.nomFichier), contenu);
    r4.parType[d.type] = (r4.parType[d.type] ?? 0) + 1;
    r4.fichiers.push({ idDocument: d.idDocument, type: d.type, lot: d.lot ?? null, extension: d.extension, nomFichier: d.nomFichier, tailleOctets: contenu.length, libelle: d.libelle, version: d.version });
  }
  R.etape4 = r4;
  ok(docs.length + ' fichiers → documents/ · ' + Object.entries(r4.parType).map(([t, n]) => t + '×' + n).join(' '));
  sauver();
}

// ── Étape 5 — export complet et tableau de vérification ─────────────────────────────────────────────────────────
async function etape5() {
  titre(5, 'Export complet et vérification');
  const r1 = R.etape1 ?? arret("l'étape 1 n'a pas été jouée");
  const r3 = R.etape3 ?? arret("l'étape 3 n'a pas été jouée");
  const prmp = PRMP();
  const g = async (u) => (await appel(prmp, 'GET', u)).corps;
  const versions = liste(await g('/api/fiches-marche/' + r3.idDmc + '/versions'));
  const fichesParVersion = {};
  for (const v of versions) fichesParVersion[v.numero ?? v.version] = await g('/api/fiches-marche/' + r3.idDmc + '/versions/' + (v.numero ?? v.version));
  const exportJeu = {
    lu: AUJOURDHUI,
    entite: await g('/api/entite-contracts/' + E.entite.id),
    prmp: await g('/api/prmps/' + E.prmp.idPrmp),
    dossier: await g('/api/dossiers/' + r1.idDossier),
    ppm: await g('/api/ppms/' + r1.idPpm),
    marche: (liste(await g('/api/marches?dossier=' + r1.idDossier)))[0],
    lots: liste(await g('/api/lots/par-marche/' + r1.idDetail)),
    previsions: liste(await g('/api/marche-previsions?marche=' + r1.idDetail)),
    agpm: JSON.parse(fs.readFileSync(path.join(ICI, 'agpm-2026.json'), 'utf8')),
    dmc: await g('/api/dmcs/' + r3.idDmc),
    fiche: await g('/api/fiches-marche/' + r3.idDmc),
    versions,
    fichesParVersion,
    articles: liste(await g('/api/fiches-marche/' + r3.idDmc + '/articles')),
    documents: liste(await g('/api/fiches-marche/' + r3.idDmc + '/documents')),
  };
  R.export = exportJeu;
  sauver();
  ok('resultat.export écrit : ' + Object.keys(exportJeu).join(', '));
  ecrireVerification(exportJeu);
  ok('verification.md écrit');
}

function ecrireVerification(x) {
  const L = [];
  const fiche = x.fiche;
  const val = (cle) => fiche.valeurs?.[cle] ?? null;
  const cell = (s) => String(s ?? '—').replace(/\|/g, '\\|').replace(/\r?\n/g, ' ');
  const court = (s, n = 90) => { const t = cell(s); return t.length > n ? t.slice(0, n - 1) + '…' : t; };
  L.push('# Jeu 2463 — tableau de correspondance');
  L.push('');
  L.push('Généré par `rejouer-2463.mjs --etape 5` le ' + AUJOURDHUI + ' depuis l\'API (valeurs en base telles que servies). Source des faits : '
    + '`frontendprs2/docs/jeu-donnees-2463-faits.md` ; nature recopiée : **[R]** repris, **[D]** déduit, **[H]** hypothèse. '
    + 'Les [H] sont aussi listées dans `docs/jeu-2463-hypotheses-backend.md`.');
  L.push('');
  L.push('## 1. Autorité contractante et acteurs');
  L.push('');
  L.push('| Fait | Stockage | Valeur en base | Nature |');
  L.push('|---|---|---|---|');
  L.push('| Autorité contractante | `tr_entite_contract.LIBELLE_ENTITE` (id ' + x.entite.idEntiteContract + ') | ' + cell(x.entite.libelleEntite) + ' | ' + E.entite.sources.libelleEntite + ' |');
  L.push('| Sigle MESupReS | *(aucune colonne)* | — | ' + E.entite.sources.sigle + ' |');
  L.push('| Adresse | `tr_entite_contract.ADRESSE` | ' + cell(x.entite.adresse) + ' | ' + E.entite.sources.adresse + ' |');
  for (const [k, s] of Object.entries(E.prmp.sources)) {
    L.push('| PRMP · ' + k + ' | `t_prmp.' + k.replace(/([A-Z])/g, '_$1').toUpperCase() + '`' + (k === 'login' ? ' → `t_compte_auth.LOGIN`' : '') + ' | ' + cell(k === 'login' ? E.prmp.login : x.prmp?.[k]) + ' | ' + s + ' |');
  }
  L.push('| Rattachement PRMP ↔ entité 11 | `t_prmp_entite` (lien ' + (R.etape0?.lien?.idPrmpEntite ?? '—') + ', actif) | ' + cell(R.etape0?.lien?.idPrmp) + ' ↔ ' + E.entite.id + ' | [D] nécessaire au modèle (une PRMP active par entité) ; lien(s) désactivé(s) : ' + cell(JSON.stringify(R.etape0?.liensDesactives ?? [])) + ' |');
  L.push('| UGPM | *(t_ugpm : personne nominative, non créée)* | — | ' + cell(E.prmp.unite.ugpm) + ' |');
  L.push('| Comptable assignataire | fiche `B03-NA-03` / `B03-NA-02` | ' + court(val('B03-NA-03')) + ' | ' + cell(E.prmp.unite.comptable) + ' |');
  L.push('');
  L.push('## 2. Dossier de planification et ligne 2463');
  L.push('');
  L.push('| Fait | Stockage | Valeur en base | Nature |');
  L.push('|---|---|---|---|');
  L.push('| Référence du dossier | `t_dossier.REFE_DOSSIER` (id ' + x.dossier.idDossier + ') | ' + cell(x.dossier.refeDossier) + ' | serveur (compteur `t_sequence_reference`) |');
  L.push('| Statut / sous-type | `t_dossier.STATUT` / `ID_SOUS_TYPE` | ' + cell(x.dossier.statut) + ' / ' + cell(x.dossier.idSousType) + ' | serveur |');
  L.push('| Exercice | `t_ppm.EXERCICE` (id ' + x.ppm.idPpm + ') | ' + cell(x.ppm.exercice) + ' | ' + E.plan.sources.exercice + ' |');
  L.push('| Date de signature du PPM | `t_ppm.DATE_SIGNATURE` | ' + cell(x.ppm.dateSignature) + ' | ' + E.plan.sources.dateSignature + ' |');
  L.push('| Signataire du PPM | `t_ppm.SIGNATAIRE` | ' + cell(x.ppm.signataire) + ' | serveur (prénoms + nom de la PRMP) |');
  L.push('| Référence du PPM / n° de mise à jour | `t_ppm.REFERENCE` / `NUM_MAJ` | ' + cell(x.ppm.reference) + ' / ' + cell(x.ppm.numMaj ?? 0) + ' | serveur |');
  L.push('| Objet | `t_marche.DESIGNATION_MARCHE` (id ' + x.marche.idDetail + ') | ' + cell(x.marche.designationMarche) + ' | ' + E.plan.sources.designationMarche + ' |');
  L.push('| Mode AOO | `t_marche.ID_MODE` | ' + cell(x.marche.idMode) + ' (' + cell(x.marche.modeLibelle ?? x.agpm.lignes[0]?.modeLibelle) + ') | ' + E.plan.sources.idMode + ' |');
  L.push('| Catégorie fournitures | `t_marche.ID_NATURE` | ' + cell(x.marche.idNature) + ' (' + cell(x.agpm.lignes[0]?.nature) + ') | ' + E.plan.sources.idNature + ' |');
  L.push('| Marché à commande | `t_marche.FORME_MARCHE` | ' + cell(x.marche.formeMarche) + ' | ' + E.plan.sources.formeMarche + ' |');
  L.push('| Financement RPI | `t_marche.FINANCEMENT` | ' + cell(x.marche.financement) + ' | ' + E.plan.sources.financement + ' |');
  L.push('| Compte 2463 | `t_marche.NUM_COMPTE` | ' + cell(x.marche.numCompte) + ' | ' + E.plan.sources.numCompte + ' |');
  L.push('| Montant estimatif | `t_marche.MONT_ESTIM` | ' + cell(montantFr(x.marche.montEstim)) + ' | ' + E.plan.sources.montEstim + ' |');
  L.push('| Imputation administrative | *(aucune colonne)* | — | ' + E.plan.sources.imputation + ' |');
  L.push('| Articles 30, 35, 63 du CMP | *(aucune colonne)* | — | ' + E.plan.sources.articlesCmp + ' |');
  L.push('');
  L.push('### Lots (`t_lot`)');
  L.push('');
  L.push('| Lot | `DESIGNATION_LOT` | `MONT_LOT` (max) | Minimum (fiche `B05-TP-02#n`) | Destination (fiche `B09-LL-01#n`) | Nature |');
  L.push('|---|---|---|---|---|---|');
  x.lots.forEach((l, i) => {
    const n = i + 1;
    L.push('| ' + n + ' (id ' + l.idLot + ') | ' + cell(l.designationLot) + ' | ' + montantFr(l.montLot) + ' | ' + cell(montantFr(val('B05-TP-02#' + n))) + ' | ' + cell(val('B09-LL-01#' + n))
      + ' | intitulé ' + E.plan.sources['lots[].designationLot'] + ' ; max ' + E.plan.sources['lots[].montLot'] + ' ; min ' + E.plan.sources['lots[].montMin'] + ' ; destination ' + E.plan.sources['lots[].destination'] + ' |');
  });
  L.push('');
  L.push('### Calendrier prévisionnel (`t_marche_prevision`) — ' + E.plan.sources.processus);
  L.push('');
  L.push('| Étape CAPM | Début | Fin | En base (id) |');
  L.push('|---|---|---|---|');
  for (const p of E.plan.processus) {
    const b = x.previsions.find((q) => q.idCapm === p.idCapm);
    L.push('| ' + p.idCapm + ' | ' + p.dateDebut + ' | ' + p.dateFin + ' | ' + (b ? (b.dateDebut === p.dateDebut && b.dateFin === p.dateFin ? 'identique' : 'DIFFÉRENT ' + b.dateDebut + '→' + b.dateFin) + ' (' + b.idPrevision + ')' : 'ABSENT') + ' |');
  }
  L.push('');
  L.push('## 3. Circuit CNM du plan — ' + E.circuit._);
  L.push('');
  L.push('| Acte | Stockage | Valeur |');
  L.push('|---|---|---|');
  L.push('| Soumission | `t_dossier.DATE_SOUMISSION`, `SOUMIS_PAR` | ' + cell(x.dossier.dateSoumission) + ' · ' + cell(x.dossier.soumisPar ?? E.prmp.login) + ' |');
  L.push('| Réception | `t_reception` (id ' + cell(R.etape1.idReception) + ') | ' + E.comptes.secretaire + ' · ' + E.circuit.dateActes + ' · complet · « ' + cell(E.circuit.reception.observation) + ' » |');
  L.push('| Dispatch | `t_dispatch` (id ' + cell(R.etape1.idDispatch) + ') | ' + E.comptes.president + ' → ' + E.comptes.membre + ' · « ' + cell(E.circuit.dispatch.instructions) + ' » |');
  L.push('| Examen | `t_examen` (id ' + cell(R.etape1.idExamen) + '), `t_examen_detail` | ' + E.comptes.membre + ' · ' + cell((R.etape1.grille ?? []).length) + ' points conformes · avis ' + E.circuit.examen.avis + ' |');
  L.push('| PV | `t_pv_examen` (id ' + cell(R.etape1.pv?.idPv) + ') | ' + cell(R.etape1.pv?.refePv) + ' · ' + cell(R.etape1.pv?.statutPv) + ' · visa ' + E.comptes.president + ' « ' + cell(E.circuit.visa.commentaire) + ' » · co-signataire ' + cell(R.etape1.pv?.imMembreCoSignataire) + ' |');
  L.push('');
  L.push('## 4. Projet d\'AGPM (dérivé, non persisté)');
  L.push('');
  L.push('| Champ | Source API | Valeur |');
  L.push('|---|---|---|');
  L.push('| agpmRequis / sous-type | `GET /api/ppms/{id}`, `GET /api/dossiers/{id}` | ' + cell(x.agpm.serveur.agpmRequis) + ' / ' + cell(x.agpm.serveur.idSousType) + ' |');
  const la = x.agpm.lignes.find((l) => l.idDetail === x.marche.idDetail) ?? {};
  for (const k of ['compte', 'nature', 'objet', 'montant', 'financement', 'modeLibelle', 'dateDao']) L.push('| ' + k + ' | calcul `calculerAgpm` (front) | ' + cell(k === 'montant' ? montantFr(la[k]) : la[k]) + ' |');
  L.push('');
  L.push('## 5. Dossier de mise en concurrence et fiche DAO');
  L.push('');
  L.push('| Fait | Stockage | Valeur en base | Nature |');
  L.push('|---|---|---|---|');
  L.push('| Référence du DAO | `t_dossier_mec.REFERENCE` (id ' + x.dmc.idDmc + ') | ' + cell(x.dmc.reference) + ' | serveur (compteur DMC) |');
  L.push('| Version validée | `t_fiche_marche` (versions ' + cell(x.versions.map((v) => v.numero ?? v.version).join(', ')) + ') | statut ' + cell(fiche.statut) + ', version ' + cell(fiche.version) + ', validée le ' + cell(fiche.dateValidation) + ' | serveur |');
  L.push('| Informations importées du plan | `FicheMarcheDto.valeursPpm` | ' + Object.keys(fiche.valeursPpm ?? {}).length + ' clés : ' + cell(Object.keys(fiche.valeursPpm ?? {}).join(', ')) + ' | [R] via le plan |');
  L.push('');
  L.push('### Cadrage (`t_fiche_marche.CADRAGE`)');
  L.push('');
  L.push('| Clé | Valeur en base | Nature |');
  L.push('|---|---|---|');
  for (const [k, v] of Object.entries(E.fiche.cadrage)) L.push('| ' + k + ' | ' + cell(fiche.cadrage?.[k]) + (String(fiche.cadrage?.[k]) === String(v) ? '' : ' (attendu ' + v + ')') + ' | ' + cell(E.fiche.cadrageSources[k]) + ' |');
  L.push('');
  L.push('### Valeurs communes (`t_fiche_marche_valeur`, clé = code)');
  L.push('');
  L.push('| Code | Valeur en base | Nature |');
  L.push('|---|---|---|');
  for (const code of Object.keys(E.fiche.valeurs).sort()) {
    const enBase = val(code);
    const conforme = enBase != null && String(enBase).trim() === String(E.fiche.valeurs[code]).trim();
    L.push('| ' + code + ' | ' + court(enBase, 110) + (conforme ? '' : ' ⚠️ (attendu : ' + court(E.fiche.valeurs[code], 60) + ')') + ' | ' + cell(E.fiche.sources[code]) + ' |');
  }
  L.push('');
  L.push('### Valeurs par lot (clé = `CODE#n`)');
  L.push('');
  L.push('| Code | Lot 1 | Lot 2 | Lot 3 | Lot 4 | Lot 5 | Nature |');
  L.push('|---|---|---|---|---|---|---|');
  for (const code of Object.keys(E.fiche.parLot)) {
    L.push('| ' + code + ' | ' + [1, 2, 3, 4, 5].map((n) => { const v = val(code + '#' + n); const a = E.fiche.parLot[code][n]; return cell(v) + (String(v) === String(a) ? '' : ' ⚠️'); }).join(' | ') + ' | ' + cell(E.fiche.sourcesParLot[code]) + ' |');
  }
  L.push('');
  L.push('### Défauts posés par le référentiel (non issus de la fiche des faits)');
  L.push('');
  L.push('| Code | Valeur en base | Provenance |');
  L.push('|---|---|---|');
  for (const [code, s] of Object.entries(E.fiche.defautsReferentiel)) L.push('| ' + code + ' | ' + cell(val(code)) + ' | ' + cell(s) + ' |');
  L.push('');
  L.push('### Besoin par lot (`t_fiche_article`, `t_fiche_caracteristique`) — ' + E.fiche.articlesSources);
  L.push('');
  L.push('| Lot | N° | Désignation | Unité | Min | Max | Caractéristiques | Nature |');
  L.push('|---|---|---|---|---|---|---|---|');
  for (const a of [...x.articles].sort((p, q) => (p.lot - q.lot) || (p.ordre - q.ordre))) {
    const dupl = E.fiche.duplications[a.lot];
    L.push('| ' + a.lot + ' | ' + a.ordre + ' | ' + cell(a.designation) + ' | ' + cell(a.unite) + ' | ' + cell(a.quantiteMin) + ' | ' + cell(a.quantiteMax) + ' | ' + (a.caracteristiques ?? []).length + ' | ' + (dupl ? '[R] identique au lot ' + dupl + ' (' + (R.etape3.identiques?.['lot' + a.lot + '=lot' + dupl] ? 'vérifié identique' : 'NON vérifié') + ')' : '[R] §3') + ' |');
  }
  L.push('');
  L.push('### Contrôles au moment de la validation');
  L.push('');
  L.push('```json');
  L.push(JSON.stringify(R.etape3.controlesAttendus ?? {}, null, 2));
  L.push('```');
  L.push('Bloquants : ' + (R.etape3.bilan?.bloquants?.length ?? '—') + ' · avertissements : ' + (R.etape3.bilan?.avertissements?.length ?? '—') + ' · ok : ' + (R.etape3.bilan?.ok?.length ?? '—') + '.');
  L.push('');
  L.push('## 6. Documents produits (`t_document_fiche_marche`, version ' + cell(fiche.version) + ')');
  L.push('');
  L.push('| Type | Lot | Extension | Fichier | Octets |');
  L.push('|---|---|---|---|---|');
  for (const d of x.documents) L.push('| ' + d.type + ' | ' + cell(d.lot) + ' | ' + d.extension + ' | `' + d.nomFichier + '` | ' + cell(d.tailleOctets) + ' |');
  L.push('');
  L.push('## 7. Paramètres');
  L.push('');
  L.push('| Paramètre | Valeur | Provenance |');
  L.push('|---|---|---|');
  for (const [k, v] of Object.entries(E.parametres)) L.push('| ' + k + ' | ' + cell(v.split(' — ')[0]) + ' | ' + cell(v.split(' — ').slice(1).join(' — ')) + ' |');
  L.push('');
  fs.writeFileSync(path.join(ICI, 'verification.md'), L.join('\n') + '\n', 'utf8');
}

const ETAPE = { 0: etape0, 1: etape1, 2: etape2, 3: etape3, 4: etape4, 5: etape5 };
for (const n of ETAPES) {
  if (!ETAPE[n]) { console.error('étape inconnue : ' + n); process.exit(1); }
  await ETAPE[n]();
}
sauver();
console.log('\nOK — état consigné dans ' + path.basename(FICHIER_JEU));
