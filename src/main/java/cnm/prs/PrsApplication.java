package cnm.prs;

import java.time.ZoneId;
import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class PrsApplication {

	/**
	 * ⚠️ 2026-10-24 (demande front « heure-serveur », fuseau signalé) — le fuseau de l'application. Toutes les heures sont des
	 * heures locales sans fuseau ({@code LocalDateTime}) : celles qu'on écrit, celles qu'on compare (heure limite de remise,
	 * séance, délais) et celle que sert {@code /api/horloge}. Elles valent donc l'heure locale de la JVM, qui était celle du poste
	 * (Europe/Paris en recette, 1 h puis 2 h de moins qu'à Antananarivo). Désormais, on l'impose avant tout le reste.
	 */
	static final String FUSEAU_DEFAUT = "Indian/Antananarivo";

	public static void main(String[] args) {
		fixerFuseau();
		SpringApplication.run(PrsApplication.class, args);
	}

	/**
	 * Le fuseau par défaut de la JVM : {@code -Dapp.fuseau}, sinon la variable {@code APP_FUSEAU}, sinon
	 * {@value #FUSEAU_DEFAUT}. Un fuseau inconnu arrête le démarrage. Appelé en tête de {@link #main}, avant que l'horloge
	 * ({@code HorlogeRecette.HORLOGE}) ne lise son fuseau.
	 */
	static ZoneId fixerFuseau() {
		String env = System.getenv("APP_FUSEAU");
		String fuseau = System.getProperty("app.fuseau", env == null || env.isBlank() ? FUSEAU_DEFAUT : env.trim());
		ZoneId zone = ZoneId.of(fuseau);
		TimeZone.setDefault(TimeZone.getTimeZone(zone));
		return zone;
	}

}
