package chat.mural.core

data class LocalizedThemeInfo(
    val title: String,
    val subtitle: String,
    val situation: String
)

object LocalizedThemes {
    private val spanish = mapOf(
        "coffee" to LocalizedThemeInfo("Un café", "Algo caliente, por favor", "Trabajas en una cafetería acogedora. Ayuda a pedir y conversa de forma natural."),
        "weekend" to LocalizedThemeInfo("El fin de semana", "Cuéntame del tuyo", "Pregunta sobre el fin de semana. Practica eventos pasados y sigue sus intereses."),
        "walk" to LocalizedThemeInfo("Un paseo", "Al aire fresco", "Da un paseo imaginario por el barrio o un parque local. Habla de la naturaleza y la vida diaria."),
        "dinner" to LocalizedThemeInfo("Planes de cena", "Cocinemos algo", "Planead la cena juntos. Pregunta por ingredientes, recetas sencillas y qué hay en la cocina."),
        "introductions" to LocalizedThemeInfo("Mucho gusto", "Empecemos por conocernos", "Conoce a alguien nuevo en una reunión o clase. Comparte de dónde eres y qué haces."),
        "groceries" to LocalizedThemeInfo("En el mercado", "Buscando lo mejor", "Ayuda a comprar en un mercado local. Practica cantidades, precios y preguntas de compra."),
        "travel" to LocalizedThemeInfo("Próxima parada", "Un billete a algún lugar", "Planea un viaje en tren. Habla de rutas y billetes sin inventar horarios reales."),
        "home" to LocalizedThemeInfo("Tu propio rincón", "Siéntete como en casa", "Habla sobre el hogar, habitaciones, mudanzas y qué hace que un lugar sea cómodo."),
        "friends" to LocalizedThemeInfo("Nuevas amistades", "Una invitación", "Eres una amistad nueva y simpática. Organizad un plan para hacer juntos."),
        "work" to LocalizedThemeInfo("Lunes por la mañana", "En la oficina", "Habla como colegas. Comenta el trabajo, reuniones y un pequeño reto por resolver."),
        "weather" to LocalizedThemeInfo("¿Lluvia otra vez?", "Haga el tiempo que haga", "Habla del clima, ropa adecuada y planes al aire libre."),
        "cabin" to LocalizedThemeInfo("Una escapada", "Días tranquilos", "Planea un fin de semana lejos: viajes, comida, paseos y descanso juntos."),
        "music" to LocalizedThemeInfo("En bucle", "¿Qué estás escuchando?", "Pregunta por la música que le gusta al usuario. Habla de sentimientos y conciertos."),
        "film" to LocalizedThemeInfo("Un episodio más", "Algo que merezca la pena", "Habla de películas y series. Pide opiniones evitando destripes indeseados."),
        "books" to LocalizedThemeInfo("Entre páginas", "Una historia memorable", "Habla sobre libros, personajes, historias y por qué importan al lector."),
        "design" to LocalizedThemeInfo("Buenas cosas", "Hechas con esmero", "Explora el diseño, la arquitectura y los objetos que admiras. Pide opiniones concretas."),
        "technology" to LocalizedThemeInfo("Lo que viene", "Ideas, herramientas y el mañana", "Habla sobre tecnología y cómo transforma el día a día."),
        "travelstories" to LocalizedThemeInfo("En otro lugar", "Un lugar que recuerdas", "Comparte historias de viajes y destinos soñados. Invita a comparar experiencias."),
        "restaurant" to LocalizedThemeInfo("Una mesa para dos", "Pide el postre", "Simula una comida en un restaurante. Practica pedidos, preferencias y resolver dudas con amabilidad."),
        "neighbours" to LocalizedThemeInfo("Puerta con puerta", "Un rostro familiar", "Habla como vecinos del barrio. Comenta el entorno y pide pequeños favores cotidianos."),
        "traditions" to LocalizedThemeInfo("Costumbres del día a día", "Pequeños gestos, grandes historias", "Explora costumbres cotidianas y tradiciones con matices."),
        "opinions" to LocalizedThemeInfo("¿Qué opinas?", "Espacio para otro punto de vista", "Elige un dilema cotidiano e invita a dar razones explorando otra perspectiva."),
        "future" to LocalizedThemeInfo("De aquí a un año", "Planes que ilusionan", "Habla sobre sueños y planes futuros. Explora posibilidades y pasos prácticos."),
        "today" to LocalizedThemeInfo("El mundo hoy", "Algo de qué hablar", "Pregunta qué tema de actualidad le interesa al estudiante para explorar hechos recientes.")
    )

    private val korean = mapOf(
        "coffee" to LocalizedThemeInfo("커피 한 잔?", "따뜻한 음료 한 잔", "아늑한 카페에서 일하고 있습니다. 주문을 돕고 자연스럽게 대화하세요."),
        "weekend" to LocalizedThemeInfo("주말 이야기", "어떻게 보냈나요?", "주말을 어떻게 보냈는지 묻고, 과거 시제를 연습하며 관심사에 대해 대화하세요."),
        "walk" to LocalizedThemeInfo("가벼운 산책", "상쾌한 공기 마시기", "함께 숲길이나 동네를 산책한다고 상상하며 자연, 날씨, 일상에 대해 이야기하세요."),
        "dinner" to LocalizedThemeInfo("저녁 식사 계획", "맛있는 요리 만들기", "함께 저녁 식사를 계획하며 재료, 좋아하는 음식, 요리 과정에 대해 이야기하세요."),
        "introductions" to LocalizedThemeInfo("처음 뵙겠습니다", "가볍게 인사 나누기", "처음 만난 사람과 자기소개를 나누고, 출신과 하는 일에 대해 대화하세요."),
        "groceries" to LocalizedThemeInfo("시장에서 장보기", "신선한 식재료 고르기", "시장에서 장을 보며 수량, 가격, 정중하게 물어보는 표현을 연습하세요."),
        "travel" to LocalizedThemeInfo("다음 목적지", "어디론가 떠나는 티켓", "기차 여행을 계획하며 노선, 목적지, 티켓 구매에 대해 이야기하세요."),
        "home" to LocalizedThemeInfo("나만의 보금자리", "편안한 우리 집", "집, 방 구조, 이사, 그리고 아늑함을 주는 것들에 대해 이야기하세요."),
        "friends" to LocalizedThemeInfo("새로운 친구", "함께 만나요", "친근한 새 친구로서 함께 할 약속이나 활동을 제안하고 계획해 보세요."),
        "work" to LocalizedThemeInfo("월요일 아침", "사무실 풍경", "직장 동료로서 업무, 회의, 그리고 해결해야 할 작은 과제에 대해 대화하세요."),
        "weather" to LocalizedThemeInfo("또 비가 오네요?", "날씨 이야기", "날씨, 어울리는 옷차림, 야외 활동 계획에 대해 이야기해 보세요."),
        "cabin" to LocalizedThemeInfo("주말 휴식", "조용한 쉼표", "자연 속 주말 여행을 계획해 보세요. 여행, 맛있는 음식, 산책과 휴식."),
        "music" to LocalizedThemeInfo("무한 반복", "요즘 무슨 노래 들어요?", "좋아하는 음악, 아티스트, 감정과 콘서트 경험에 대해 이야기하세요."),
        "film" to LocalizedThemeInfo("한 편만 더", "볼만한 작품 추천", "영화와 드라마에 대해 이야기하세요. 감상평을 나누되 스포일러는 피하세요."),
        "books" to LocalizedThemeInfo("책장 너머로", "기억에 남는 이야기", "인상 깊었던 책, 주인공, 줄거리와 그것이 마음에 남은 이유를 이야기하세요."),
        "design" to LocalizedThemeInfo("디자인과 공간", "정성 들여 만든 것들", "좋아하는 디자인, 건축, 일상 속 멋진 사물에 대해 구체적으로 이야기하세요."),
        "technology" to LocalizedThemeInfo("다가오는 미래", "기술과 일상의 변화", "기술 혁신과 새로운 도구들이 일상생활을 어떻게 바꾸고 있는지 논의해 보세요."),
        "travelstories" to LocalizedThemeInfo("어딘가 먼 곳", "기억에 남는 여행지", "여행 추억과 가보고 싶은 꿈의 여행지에 대해 이야기를 나누세요."),
        "restaurant" to LocalizedThemeInfo("두 사람을 위한 테이블", "디저트까지 즐기기", "식당에서의 식사 장면을 연출해 보세요. 메뉴 주문과 취향 표현 연습."),
        "neighbours" to LocalizedThemeInfo("이웃사촌", "반가운 얼굴", "이웃으로서 동네 생활에 대해 이야기하고 가벼운 도움을 주고받으세요."),
        "traditions" to LocalizedThemeInfo("일상 속 풍습", "작은 관습, 큰 이야기", "다양한 문화의 일상 풍습과 전통에 대해 섬세하게 이야기하세요."),
        "opinions" to LocalizedThemeInfo("어떻게 생각하세요?", "다양한 관점 나누기", "일상의 흥미로운 고민에 대해 서로의 이유를 나누고 다양한 관점을 탐색하세요."),
        "future" to LocalizedThemeInfo("1년 후 나의 모습", "기대되는 미래 계획", "앞으로의 희망과 계획에 대해 이야기하며 구체적인 실천 단계를 나눠보세요."),
        "today" to LocalizedThemeInfo("오늘의 세상", "생생한 이야기", "학습자가 관심 있는 시사 주제에 대해 묻고 사실에 기반하여 토론하세요.")
    )

    private val japanese = mapOf(
        "coffee" to LocalizedThemeInfo("コーヒーでも？", "温かいものをどうぞ", "居心地のよいカフェで働いています。注文を手伝い、自然に会話を楽しみましょう。"),
        "weekend" to LocalizedThemeInfo("週末の予定", "どんな週末でしたか？", "週末の出来事について尋ね、過去形を練習しながら楽しく話しましょう。"),
        "walk" to LocalizedThemeInfo("お散歩", "爽やかな風を感じて", "公園や森を散歩する場面を想像し、自然や天気、日々の生活について話しましょう。"),
        "dinner" to LocalizedThemeInfo("今晩の夕食", "何を作りましょうか？", "夕食の献立を一緒に考えましょう。食材や好み、簡単な料理手順について話します。"),
        "introductions" to LocalizedThemeInfo("はじめまして", "まずは自己紹介から", "初対面の挨拶を交わし、出身や仕事、趣味について気軽に話しましょう。"),
        "groceries" to LocalizedThemeInfo("市場でお買い物", "新鮮な食材を探して", "地元の青空市場やスーパーで買い物。量や値段、丁寧な尋ね方を練習します。"),
        "travel" to LocalizedThemeInfo("次の停車駅", "旅の切符を手にして", "電車の旅を計画しましょう。行き先や路線、切符の購入について会話します。"),
        "home" to LocalizedThemeInfo("自分のお部屋", "くつろげる空間", "家や部屋の間取り、引っ越し、快適な暮らしについて話しましょう。"),
        "friends" to LocalizedThemeInfo("新しい友達", "一緒に出かけませんか？", "気さくな友人として、週末に一緒に行う楽しい予定を立てましょう。"),
        "work" to LocalizedThemeInfo("月曜日の朝", "オフィスにて", "同僚として仕事や会議、ちょっとした課題の解決について会話しましょう。"),
        "weather" to LocalizedThemeInfo("また雨？", "どんな天気でも", "天気や服装、お出かけの予定について気楽に話しましょう。"),
        "cabin" to LocalizedThemeInfo("週末の小旅行", "静かで穏やかな時間", "週末の旅行を計画。移動、美味しい食事、散策やリフレッシュについて話しましょう。"),
        "music" to LocalizedThemeInfo("リピート再生", "今何を聴いていますか？", "お気に入りの音楽やアーティスト、感動したライブについて語り合いましょう。"),
        "film" to LocalizedThemeInfo("もう1話だけ", "おすすめの作品", "映画やドラマについて意見を交わしましょう。ネタバレに配慮しながら話します。"),
        "books" to LocalizedThemeInfo("ページをめくって", "心に残る物語", "好きな本や登場人物、物語の魅力についてじっくり語り合いましょう。"),
        "design" to LocalizedThemeInfo("こだわりのデザイン", "丁寧に作られたもの", "デザインや建築、愛着のある道具について具体的な感想を交わしましょう。"),
        "technology" to LocalizedThemeInfo("これからの未来", "新しい道具と明日", "テクノロジーが日々の生活をどのように変えているかについて話しましょう。"),
        "travelstories" to LocalizedThemeInfo("思い出の場所", "忘れられない旅", "旅の思い出や行ってみたい国・街について楽しく語り合いましょう。"),
        "restaurant" to LocalizedThemeInfo("2人席のテーブル", "デザートまで楽しんで", "レストランでの食事をロールプレイ。注文や好みの伝え方を練習します。"),
        "neighbours" to LocalizedThemeInfo("お隣さん", "見慣れた笑顔", "ご近所さん同士として、地域の話題やちょっとした頼み事について話しましょう。"),
        "traditions" to LocalizedThemeInfo("日々の習慣と伝統", "小さな慣習、豊かな物語", "日常の風習や行事について、文化の多様性を尊重しながら話しましょう。"),
        "opinions" to LocalizedThemeInfo("どう思いますか？", "異なる視点に触れて", "身近なテーマを取り上げ、理由を述べ合いながら別の見方を考えてみましょう。"),
        "future" to LocalizedThemeInfo("1年後の未来", "楽しみにしている計画", "夢や将来の計画について語り、具体的な一歩を考えてみましょう。"),
        "today" to LocalizedThemeInfo("今、世界では", "旬のトピック", "今気になっている時事ネタについて尋ね、事実に基づきながら会話を深めましょう。")
    )

    private val hindi = mapOf(
        "coffee" to LocalizedThemeInfo("एक कप कॉफ़ी?", "कुछ गरमा-गरम", "आप एक आरामदायक कैफ़े में हैं। ऑर्डर करने में मदद करें और बातचीत करें।"),
        "weekend" to LocalizedThemeInfo("सप्ताहांत", "अपने वीकेंड के बारे में बताएं", "सप्ताहांत की योजनाओं या बीती बातों के बारे में पूछें और अभ्यास करें।"),
        "walk" to LocalizedThemeInfo("थोड़ी सैर", "ताज़ी हवा में", "पार्क या जंगल में सैर की कल्पना करें और प्रकृति व मौसम पर चर्चा करें।"),
        "dinner" to LocalizedThemeInfo("रात के खाने की तैयारी", "कुछ अच्छा बनाते हैं", "रात के खाने की योजना बनाएं। सामग्री, पसंद और पकाने के तरीकों पर बात करें।"),
        "introductions" to LocalizedThemeInfo("आपसे मिलकर खुशी हुई", "परिचय से शुरुआत करें", "पहली बार किसी से मिलें। अपना परिचय दें और एक-दूसरे के बारे में जानें।"),
        "groceries" to LocalizedThemeInfo("सब्ज़ी बाज़ार में", "ताज़ी चीज़ों की खरीदारी", "बाज़ार में खरीदारी करें। मात्रा, मूल्य और सवाल पूछने का अभ्यास करें।"),
        "travel" to LocalizedThemeInfo("अगला पड़ाव", "सफ़र का टिकट", "ट्रेन यात्रा की योजना बनाएं। रास्तों, टिकटों और दिशाओं पर बात करें।"),
        "home" to LocalizedThemeInfo("अपना घर", "आराम से महसूस करें", "घर, कमरों, रहने की जगह और सुविधा पर बात करें।"),
        "friends" to LocalizedThemeInfo("नए दोस्त", "एक मुलाक़ात का न्योता", "एक मिलनसार दोस्त की तरह साथ में कुछ करने का कार्यक्रम बनाएं।"),
        "work" to LocalizedThemeInfo("सोमवार की सुबह", "दफ़्तर में", "सहकर्मियों की तरह काम, बैठकों और समस्याओं के समाधान पर चर्चा करें।"),
        "weather" to LocalizedThemeInfo("फिर से बारिश?", "मौसम का मिज़ाज", "मौसम, कपड़ों और बाहर जाने की योजनाओं पर बात करें।"),
        "cabin" to LocalizedThemeInfo("छुट्टियों की सैर", "शांत और सुकून भरे दिन", "वीकेंड ट्रिप की योजना बनाएं: यात्रा, खान-पान, सैर और आराम।"),
        "music" to LocalizedThemeInfo("पसंदीदा धुन", "आप क्या सुन रहे हैं?", "पसंदीदा संगीत, गानों, कलाकारों और संगीत समारोहों पर चर्चा करें।"),
        "film" to LocalizedThemeInfo("एक और एपिसोड", "कुछ देखने लायक", "फ़िल्मों और सीरीज़ पर चर्चा करें और राय साझा करें।"),
        "books" to LocalizedThemeInfo("किताबों के पन्ने", "एक यादगार कहानी", "पुस्तकों, पात्रों और कहानियों के महत्व पर बात करें।"),
        "design" to LocalizedThemeInfo("सुंदर डिज़ाइन", "सावधानी से निर्मित", "डिज़ाइन, वास्तुकला और अपनी पसंदीदा चीज़ों पर चर्चा करें।"),
        "technology" to LocalizedThemeInfo("आने वाला कल", "तकनीक और बदलाव", "प्रौद्योगिकी और दैनिक जीवन में उसके प्रभावों पर बातचीत करें।"),
        "travelstories" to LocalizedThemeInfo("सफ़र की यादें", "एक यादगार जगह", "यात्रा के अनुभव और अपनी पसंदीदा जगहों के बारे में साझा करें।"),
        "restaurant" to LocalizedThemeInfo("दो लोगों की मेज़", "स्वादिष्ट भोजन", "रेस्तरां में खाने की भूमिका निभाएं। ऑर्डर करने और पसंद बताने का अभ्यास करें।"),
        "neighbours" to LocalizedThemeInfo("पड़ोसी", "एक जाना-पहचाना चेहरा", "पड़ोसियों की तरह बातें करें और आसपास के माहौल पर चर्चा करें।"),
        "traditions" to LocalizedThemeInfo("दैनिक परंपराएं", "रीति-रिवाज़ और कहानियां", "संस्कृतियों और दैनिक रीति-रिवाजों पर समझदारी से चर्चा करें।"),
        "opinions" to LocalizedThemeInfo("आपकी क्या राय है?", "एक और दृष्टिकोण", "दैनिक जीवन के मुद्दों पर विचार रखें और विभिन्न दृष्टिकोणों को समझें।"),
        "future" to LocalizedThemeInfo("एक साल बाद", "उम्मीदों भरे सपने", "भविष्य की योजनाओं और आशाओं पर विचार करें।"),
        "today" to LocalizedThemeInfo("आज की दुनिया", "चर्चा के विषय", "वर्तमान घटनाओं और ताज़ा मुद्दों पर विचार साझा करें।")
    )

    private val portuguese = mapOf(
        "coffee" to LocalizedThemeInfo("Um cafezinho?", "Algo quentinho, por favor", "Você trabalha em um café acolhedor. Ajude a fazer o pedido e converse naturalmente."),
        "weekend" to LocalizedThemeInfo("O fim de semana", "Me conte sobre o seu", "Pergunte sobre o fim de semana. Pratique acontecimentos passados e interesses."),
        "walk" to LocalizedThemeInfo("Uma caminhada", "Um pouco de ar fresco", "Dê um passeio imaginário pelo bairro ou parque. Conversem sobre natureza e cotidiano."),
        "dinner" to LocalizedThemeInfo("O jantar", "Vamos preparar algo", "Planejem o jantar juntos. Conversem sobre ingredientes, receitas e preferências."),
        "introductions" to LocalizedThemeInfo("Prazer em conhecer", "Comece aos poucos", "Conheça alguém novo em um encontro ou aula. Fale de onde você é e o que faz."),
        "groceries" to LocalizedThemeInfo("Na feira", "Escolhendo o melhor", "Compre em uma feira ou mercado local. Pratique quantidades, preços e perguntas."),
        "travel" to LocalizedThemeInfo("Pé na estrada", "Uma passagem para algum lugar", "Planeje uma viagem de trem. Fale sobre rotas, bilhetes e destinos."),
        "home" to LocalizedThemeInfo("Um cantinho seu", "Sinta-se em casa", "Fale sobre a casa, cômodos, mudanças e o que torna um lugar acolhedor."),
        "friends" to LocalizedThemeInfo("Novos amigos", "Um convite", "Você é uma nova amizade simpática. Combinem uma atividade para fazer juntos."),
        "work" to LocalizedThemeInfo("Segunda-feira de manhã", "No escritório", "Conversem como colegas de trabalho. Falem sobre projetos, reuniões e desafios."),
        "weather" to LocalizedThemeInfo("Chuva de novo?", "Venha o tempo que vier", "Fale sobre o clima, roupas adequadas e planos ao ar livre."),
        "cabin" to LocalizedThemeInfo("Fim de semana fora", "Dias de tranquilidade", "Planeje um fim de semana de refúgio: viagem, comida gostosa e descanso."),
        "music" to LocalizedThemeInfo("No repeat", "O que você está ouvindo?", "Pergunte sobre as músicas favoritas, sentimentos, bandas e shows."),
        "film" to LocalizedThemeInfo("Só mais um episódio", "Algo que vale a pena assistir", "Conversem sobre filmes e séries. Troquem opiniões sem dar spoilers."),
        "books" to LocalizedThemeInfo("Entre as páginas", "Uma história marcante", "Conversem sobre livros, personagens favoritos e histórias marcantes."),
        "design" to LocalizedThemeInfo("Boas ideias", "Feitas com carinho", "Explore design, arquitetura e objetos bem pensados. Peça opiniões práticas."),
        "technology" to LocalizedThemeInfo("O que vem a seguir", "Ideias, ferramentas e o amanhã", "Discutam sobre tecnologia e como ela transforma nossa rotina diária."),
        "travelstories" to LocalizedThemeInfo("Em outro lugar", "Um lugar marcante", "Troquem histórias de viagens e destinos dos sonhos com comparações ricas."),
        "restaurant" to LocalizedThemeInfo("Uma mesa para dois", "Espaço para a sobremesa", "Pratique pedir refeições em um restaurante, com pedidos e preferências."),
        "neighbours" to LocalizedThemeInfo("Porta com porta", "Um rosto conhecido", "Conversem como vizinhos. Falem sobre o bairro e pequenos favores do dia a dia."),
        "traditions" to LocalizedThemeInfo("Costumes do dia a dia", "Pequenos hábitos, grandes histórias", "Explore costumes e tradições com sensibilidade cultural."),
        "opinions" to LocalizedThemeInfo("O que você acha?", "Espaço para outro olhar", "Escolham um dilema simples. Apresentem argumentos e vejam outros pontos de vista."),
        "future" to LocalizedThemeInfo("Daqui a um ano", "Planos que inspiram", "Conversem sobre expectativas e projetos para o futuro com passos práticos."),
        "today" to LocalizedThemeInfo("O mundo hoje", "Algo para conversar", "Pergunte sobre um assunto da atualidade e conversem sobre os fatos.")
    )

    private val french = mapOf(
        "coffee" to LocalizedThemeInfo("Un café ?", "Quelque chose de chaud, s\'il vous plaît", "Vous travaillez dans un café chaleureux. Aidez à commander, puis discutez naturellement."),
        "weekend" to LocalizedThemeInfo("Le week-end", "Racontez-moi le vôtre", "Demandez des nouvelles du week-end. Échangez sur les événements passés et leurs centres d\'intérêt."),
        "walk" to LocalizedThemeInfo("Une promenade", "Prendre un grand bol d\'air", "Imaginez une promenade en forêt ou au parc. Parlez de la nature, du temps et du quotidien."),
        "dinner" to LocalizedThemeInfo("Le dîner", "Préparons un bon repas", "Planifiez le dîner ensemble. Échangez sur les ingrédients, les recettes et vos préférences."),
        "introductions" to LocalizedThemeInfo("Ravi de vous rencontrer", "Faisons connaissance", "Rencontrez quelqu\'un pour la première fois. Présentez-vous et partagez vos centres d\'intérêt."),
        "groceries" to LocalizedThemeInfo("Au marché", "Trouver de bons produits", "Faites des courses au marché local. Pratiquez les quantités, les prix et les questions."),
        "travel" to LocalizedThemeInfo("Prochain arrêt", "Un billet pour l\'aventure", "Planifiez un voyage en train. Discutez des itinéraires et des billets."),
        "home" to LocalizedThemeInfo("Chez soi", "Faites comme chez vous", "Parlez du logement, des pièces, d\'un déménagement et de ce qui rend un lieu chaleureux."),
        "friends" to LocalizedThemeInfo("Nouveaux amis", "Une invitation", "Faites la connaissance d\'un nouvel ami et organisez une sortie ensemble."),
        "work" to LocalizedThemeInfo("Lundi matin", "Au bureau", "Discutez comme des collègues : le travail, les réunions et un défi à relever."),
        "weather" to LocalizedThemeInfo("Encore de la pluie ?", "Quel que soit le temps", "Discutez de la météo, des vêtements adaptés et des projets en plein air."),
        "cabin" to LocalizedThemeInfo("Une escapade", "Un moment de calme", "Planifiez un week-end au vert : voyage, repas, balades et détente."),
        "music" to LocalizedThemeInfo("En boucle", "Qu\'écoutez-vous en ce moment ?", "Parlez de musique, d\'artistes préférés, d\'émotions et de concerts."),
        "film" to LocalizedThemeInfo("Encore un épisode", "Quelque chose à regarder", "Discutez de films et de séries. Donnez vos avis sans dévoiler la fin."),
        "books" to LocalizedThemeInfo("Au fil des pages", "Une histoire marquante", "Parlez de livres, de personnages et de ce qui vous a touché dans l\'histoire."),
        "design" to LocalizedThemeInfo("De belles choses", "Conçu avec soin", "Explorez le design, l\'architecture et les beaux objets. Partagez des avis concrets."),
        "technology" to LocalizedThemeInfo("Ce qui nous attend", "Idées, outils et avenir", "Discutez de la technologie et de son impact sur la vie quotidienne."),
        "travelstories" to LocalizedThemeInfo("Ailleurs", "Un souvenir de voyage", "Partagez vos souvenirs de voyage et les destinations de vos rêves."),
        "restaurant" to LocalizedThemeInfo("Une table pour deux", "Place au dessert", "Mettez en scène un repas au restaurant : commandes, préférences et formules de politesse."),
        "neighbours" to LocalizedThemeInfo("Entre voisins", "Un visage familier", "Discutez comme des voisins : le quartier et les petits coups de main du quotidien."),
        "traditions" to LocalizedThemeInfo("Coutumes du quotidien", "Petits rituels, grandes histoires", "Découvrez les coutumes et traditions du quotidien avec nuance et respect."),
        "opinions" to LocalizedThemeInfo("Qu\'en pensez-vous ?", "La place pour un autre regard", "Discutez d\'un dilemme du quotidien. Argumentez et confrontez vos points de vue."),
        "future" to LocalizedThemeInfo("D\'ici un an", "Des projets stimulants", "Parlez de vos espoirs et de vos projets d\'avenir avec des étapes concrètes."),
        "today" to LocalizedThemeInfo("Le monde aujourd\'hui", "Un sujet d\'actualité", "Demandez quel thème d\'actualité intéresse votre interlocuteur pour en discuter.")
    )

    private val german = mapOf(
        "coffee" to LocalizedThemeInfo("Ein Kaffee?", "Etwas Warmes, bitte", "Sie arbeiten in einem gemütlichen Café. Helfen Sie bei der Bestellung und plaudern Sie ganz ungezwungen."),
        "weekend" to LocalizedThemeInfo("Das Wochenende", "Erzählen Sie von Ihrem", "Fragen Sie nach dem Wochenende. Üben Sie Vergangenes und sprechen Sie über Interessen."),
        "walk" to LocalizedThemeInfo("Ein Spaziergang", "Raus an die frische Luft", "Machen Sie einen gedachten Spaziergang im Wald oder Park. Sprechen Sie über Natur und Alltag."),
        "dinner" to LocalizedThemeInfo("Abendessen", "Kochen wir etwas Feines", "Planen Sie das Abendessen. Sprechen Sie über Zutaten, Rezepte und Vorlieben."),
        "introductions" to LocalizedThemeInfo("Schön Sie kennenzulernen", "Fangen wir klein an", "Lernen Sie jemanden kennen. Stellen Sie sich vor und sprechen Sie über Herkunft und Beruf."),
        "groceries" to LocalizedThemeInfo("Auf dem Markt", "Frische Zutaten finden", "Kaufen Sie auf dem Wochenmarkt ein. Üben Sie Mengenangaben, Preise und Nachfragen."),
        "travel" to LocalizedThemeInfo("Nächster Halt", "Eine Fahrkarte ins Blaue", "Planen Sie eine Zugreise. Sprechen Sie über Strecken, Fahrkarten und Reiseziele."),
        "home" to LocalizedThemeInfo("Das eigene Zuhause", "Fühlen Sie sich wie daheim", "Sprechen Sie über Wohnen, Zimmer, Umzug und Gemütlichkeit."),
        "friends" to LocalizedThemeInfo("Neue Freunde", "Eine Einladung", "Sie sind ein freundlicher Bekannter. Verabreden Sie sich für eine gemeinsame Aktivität."),
        "work" to LocalizedThemeInfo("Montagmorgen", "Im Büro", "Plaudern Sie wie Kollegen. Sprechen Sie über Arbeit, Meetings und Aufgaben."),
        "weather" to LocalizedThemeInfo("Schon wieder Regen?", "Bei jedem Wetter", "Sprechen Sie über das Wetter, Kleidung und Aktivitäten im Freien."),
        "cabin" to LocalizedThemeInfo("Ein Wochenende weg", "Ein ruhigerer Tag", "Planen Sie einen Kurzurlaub: Anreise, gutes Essen, Spaziergänge und Erholung."),
        "music" to LocalizedThemeInfo("In Dauerschleife", "Was hören Sie gerade?", "Sprechen Sie über Lieblingsmusik, Gefühle, Bands und Konzerte."),
        "film" to LocalizedThemeInfo("Noch eine Folge", "Etwas Sehenswertes", "Tauschen Sie sich über Filme und Serien aus, ohne die Handlung zu verraten."),
        "books" to LocalizedThemeInfo("Zwischen den Seiten", "Eine unvergessliche Geschichte", "Sprechen Sie über Bücher, Charaktere und Geschichten, die berühren."),
        "design" to LocalizedThemeInfo("Gute Dinge", "Mit Sorgfalt gemacht", "Entdecken Sie Design, Architektur und schöne Objekte. Bitten Sie um konkrete Ansichten."),
        "technology" to LocalizedThemeInfo("Was als Nächstes kommt", "Ideen, Werkzeuge und morgen", "Sprechen Sie über Technologie und wie sie unseren Alltag verändert."),
        "travelstories" to LocalizedThemeInfo("Anderswo", "Ein unvergesslicher Ort", "Tauschen Sie Reiseerinnerungen und Traumziele aus."),
        "restaurant" to LocalizedThemeInfo("Ein Tisch für zwei", "Noch ein Dessert", "Spielen Sie einen Restaurantbesuch nach: Bestellen, Vorlieben und Höflichkeit."),
        "neighbours" to LocalizedThemeInfo("Nebenan", "Ein bekanntes Gesicht", "Plaudern Sie wie Nachbarn über das Viertel und kleine gegenseitige Hilfen."),
        "traditions" to LocalizedThemeInfo("Alltägliche Bräuche", "Kleine Rituale, große Geschichten", "Erkunden Sie Alltagsgewohnheiten und Bräuche mit Fingerspitzengefühl."),
        "opinions" to LocalizedThemeInfo("Was meinen Sie?", "Raum für andere Sichtweisen", "Wählen Sie ein Dilemma und beleuchten Sie verschiedene Perspektiven mit Begründungen."),
        "future" to LocalizedThemeInfo("In einem Jahr", "Zukunftspläne, die begeistern", "Sprechen Sie über Wünsche und Zukunftspläne sowie nächste Schritte."),
        "today" to LocalizedThemeInfo("Die Welt heute", "Gesprächsstoff", "Fragen Sie nach aktuellen Themen und diskutieren Sie sachlich darüber.")
    )

    private val italian = mapOf(
        "coffee" to LocalizedThemeInfo("Un caffè?", "Qualcosa di caldo, per favore", "Lavori in un accogliente bar. Aiuta con l\'ordine e chiacchiera con naturalezza."),
        "weekend" to LocalizedThemeInfo("Il fine settimana", "Raccontami del tuo", "Chiedi del fine settimana. Fai pratica sugli eventi passati seguendo gli interessi."),
        "walk" to LocalizedThemeInfo("Una passeggiata", "All\'aria aperta", "Immaginate una passeggiata nella natura. Parlate di ambiente, tempo e vita quotidiana."),
        "dinner" to LocalizedThemeInfo("Piani per cena", "Prepariamo qualcosa", "Pianificate la cena insieme. Parlate di ingredienti, ricette semplici e preferenze."),
        "introductions" to LocalizedThemeInfo("Piacere di conoscerti", "Iniziamo dalle presentazioni", "Incontra una persona nuova. Presentati e condividi le tue passioni."),
        "groceries" to LocalizedThemeInfo("Al mercato", "Scegliere gli ingredienti migliori", "Fai la spesa al mercato rionale. Esercitati con quantità, prezzi e richieste."),
        "travel" to LocalizedThemeInfo("Prossima fermata", "Un biglietto per partire", "Pianifica un viaggio in treno. Parla di itinerari, fermate e biglietti."),
        "home" to LocalizedThemeInfo("Una casa tutta per sé", "Sentiti a casa tua", "Parla della casa, delle stanze, del trasloco e di cosa rende accogliente un posto."),
        "friends" to LocalizedThemeInfo("Nuovi amici", "Un invito", "Sei un nuovo conoscente socievole. Organizzate un\'attività da fare insieme."),
        "work" to LocalizedThemeInfo("Lunedì mattina", "In ufficio", "Chiacchierate da colleghi. Parlate di lavoro, riunioni e piccoli problemi da risolvere."),
        "weather" to LocalizedThemeInfo("Piove ancora?", "Con qualsiasi tempo", "Parla del meteo, di come vestirsi e dei programmi all\'aperto."),
        "cabin" to LocalizedThemeInfo("Un fine settimana via", "Giornate tranquille", "Pianificate un weekend fuori: viaggio, buon cibo, passeggiate e relax."),
        "music" to LocalizedThemeInfo("In ripetizione", "Cosa stai ascoltando?", "Chiedi della musica preferita. Esplorate emozioni, brani del cuore e concerti."),
        "film" to LocalizedThemeInfo("Ancora un episodio", "Qualcosa da guardare", "Discuti di film e serie tv. Scambiatevi opinioni evitando spoiler."),
        "books" to LocalizedThemeInfo("Tra le pagine", "Una storia indimenticabile", "Parla di libri, personaggi e del perché certe storie ti sono rimaste nel cuore."),
        "design" to LocalizedThemeInfo("Belle cose", "Fatte con cura", "Esplora design, architettura e oggetti ben curati. Chiedi pareri precisi."),
        "technology" to LocalizedThemeInfo("Cosa ci riserva il futuro", "Idee, strumenti e domani", "Parla di tecnologia e di come sta trasformando le nostre giornate."),
        "travelstories" to LocalizedThemeInfo("Da qualche altra parte", "Un posto che ricordi", "Scambiatevi racconti di viaggio e mete desiderate."),
        "restaurant" to LocalizedThemeInfo("Un tavolo per due", "C\'è spazio per il dolce", "Simulate un pranzo al ristorante. Fai pratica con ordinazioni e richieste gentili."),
        "neighbours" to LocalizedThemeInfo("Vicini di casa", "Un volto familiare", "Chiacchierate da vicini. Parlate del quartiere e di piccoli aiuti quotidiani."),
        "traditions" to LocalizedThemeInfo("Tradizioni quotidiane", "Piccoli usi, grandi storie", "Esplora usanze e tradizioni quotidiane con delicatezza e rispetto."),
        "opinions" to LocalizedThemeInfo("Cosa ne pensi?", "Spazio per un altro punto di vista", "Scegliete un dilemma quotidiano. Spiega le tue ragioni ed esplorate altri punti di vista."),
        "future" to LocalizedThemeInfo("Tra un anno", "Piani di cui parlare", "Parla di speranze e progetti futuri, esplorando passi concreti."),
        "today" to LocalizedThemeInfo("Il mondo oggi", "Qualcosa di cui parlare", "Chiedi quale tema di attualità incuriosisce l\'interlocutore per parlarne insieme.")
    )

    private val chinese = mapOf(
        "coffee" to LocalizedThemeInfo("喝杯咖啡？", "来杯温热的饮品", "你在一家温馨的咖啡馆工作。协助点单并自然地展开日常交流。"),
        "weekend" to LocalizedThemeInfo("周末时光", "聊聊你的周末", "询问学习者的周末经历，练习过去发生的事情并交流兴趣爱好。"),
        "walk" to LocalizedThemeInfo("散散步", "呼吸新鲜空气", "设想一起在公园或林间漫步，聊聊自然风光、天气变化与日常生活。"),
        "dinner" to LocalizedThemeInfo("晚餐吃什么", "一起做点好吃的", "共同规划今晚的晚餐，讨论食材、烹饪步骤以及各自的饮食偏好。"),
        "introductions" to LocalizedThemeInfo("很高兴认识你", "轻松的初次相识", "初次结识新朋友，通过自然亲切的自我介绍分享来自哪里以及平时的工作生活。"),
        "groceries" to LocalizedThemeInfo("去买菜", "挑选新鲜食材", "在菜市场或超市采购，练习询问数量、价格以及礼貌的购物表达。"),
        "travel" to LocalizedThemeInfo("下一站", "出发的车票", "规划一次火车旅行，讨论交通方式、路线以及如何购票。"),
        "home" to LocalizedThemeInfo("温馨小家", "宾至如归", "聊聊居住环境、房间布置、搬家经历以及如何让家里更舒适惬意。"),
        "friends" to LocalizedThemeInfo("结识新友", "朋友的邀约", "作为一位热情友好的新朋友，相约一起做些有趣的事情。"),
        "work" to LocalizedThemeInfo("周一早晨", "办公室里", "像同事一样交谈，聊聊工作日程、会议安排以及需要解决的问题。"),
        "weather" to LocalizedThemeInfo("又下雨了吗？", "无论阴晴", "聊聊天气情况、穿衣搭配以及适合当下的户外休闲安排。"),
        "cabin" to LocalizedThemeInfo("周末远足", "宁静的一天", "计划一次远离尘嚣的周末短途游：路途安排、地道美食、林间漫步与放松身心。"),
        "music" to LocalizedThemeInfo("循环播放", "你最近在听什么歌？", "聊聊喜欢的音乐风格、常听的歌手、旋律带来的感受以及演唱会经历。"),
        "film" to LocalizedThemeInfo("再看一集", "值得一看的好剧", "交流精彩的电影与剧集，分享观后感并避免不必要的剧透。"),
        "books" to LocalizedThemeInfo("纸间温情", "回味无穷的故事", "畅谈喜爱的书籍、书中人物以及触动心灵的篇章故事。"),
        "design" to LocalizedThemeInfo("美好设计", "匠心之作", "探索建筑设计与精致的生活物件，交流对设计美学与实用性的见解。"),
        "technology" to LocalizedThemeInfo("未来已来", "科技、工具与明天", "讨论前沿科技如何潜移默化地改变我们的日常生活与工作方式。"),
        "travelstories" to LocalizedThemeInfo("远方足迹", "难忘的目的地", "分享旅途趣事与向往的胜地，生动描述异域风情与旅行体验。"),
        "restaurant" to LocalizedThemeInfo("预订双人座", "甜点时光", "模拟在餐厅用餐的场景，练习如何点菜、表达饮食禁忌与礼貌沟通。"),
        "neighbours" to LocalizedThemeInfo("邻里之间", "熟悉的面孔", "像邻居一样攀谈，聊聊社区环境、周边生活与邻里间的小照应。"),
        "traditions" to LocalizedThemeInfo("日常风俗", "细微习俗，生动故事", "细致探讨各地的生活习俗与节庆传统，尊重不同地域的文化多样性。"),
        "opinions" to LocalizedThemeInfo("你怎么看？", "倾听不同的声音", "选择生活中的两难小抉择，表达各自的理由并包容多元视角。"),
        "future" to LocalizedThemeInfo("一年之后", "憧憬未来的计划", "聊聊对未来的期许与规划，探索多种可能性以及切实可行的行动步骤。"),
        "today" to LocalizedThemeInfo("今日世界", "畅聊时下热点", "询问学习者对哪些当前新闻话题感兴趣，并基于可靠事实展开讨论。")
    )

    private val norwegian = mapOf(
        "coffee" to LocalizedThemeInfo("En kaffe?", "Noe varmt, takk", "Du jobber på en koselig kafé. Hjelp med bestillingen og slå av en hyggelig prat."),
        "weekend" to LocalizedThemeInfo("Helgen", "Fortell om din", "Spør om helgen. Øv på fortid og følg interessene."),
        "walk" to LocalizedThemeInfo("En liten tur", "Ut i frisk luft", "Gå en tenkt skogstur sammen. Prat om naturen, været og hverdagen."),
        "dinner" to LocalizedThemeInfo("Middagsplaner", "La oss lage noe godt", "Planlegg middag sammen. Prat om ingredienser, oppskrifter og hva som er på kjøkkenet."),
        "introductions" to LocalizedThemeInfo("Hyggelig å møte deg", "Start med en hilsen", "Møt noen for første gang. Del litt om hvem du er og hva du driver med."),
        "groceries" to LocalizedThemeInfo("På markedet", "Finne de gode tomatene", "Hjelp til med å handle på et matmarked. Øv på mengder, priser og spørsmål."),
        "travel" to LocalizedThemeInfo("Neste stopp", "En billett et sted", "Planlegg en togreise. Snakk om ruter, billetter og reisemål."),
        "home" to LocalizedThemeInfo("Et eget sted", "Føl deg som hjemme", "Snakk om hjemmet, rom, flytting og hva som gjør et sted trivelig."),
        "friends" to LocalizedThemeInfo("Nye venner", "En invitasjon", "Du er en hyggelig ny bekjent. Avtal noe hyggelig å finne på sammen."),
        "work" to LocalizedThemeInfo("Mandag morgen", "På kontoret", "Prat som kolleger. Snakk om arbeid, møter og en liten oppgave som må løses."),
        "weather" to LocalizedThemeInfo("Regn igjen?", "Uansett vær", "Snakk om været, klær og planer utendørs."),
        "cabin" to LocalizedThemeInfo("Hyttetur", "En roligere dag", "Planlegg en hyttetur: reise, god mat, turer og avslapping."),
        "music" to LocalizedThemeInfo("På repeat", "Hva hører du på?", "Spør om musikk, favoritter, følelser og konserter."),
        "film" to LocalizedThemeInfo("En episode til", "Noe verdt å se", "Diskuter filmer og serier. Be om meninger og unngå avsløringer."),
        "books" to LocalizedThemeInfo("Mellom permene", "En historie som festet seg", "Prat om bøker, karakterer og fortellinger som har gjort inntrykk."),
        "design" to LocalizedThemeInfo("Gode ting", "Laget med omtanke", "Utforsk design, arkitektur og gjenstander du setter pris på."),
        "technology" to LocalizedThemeInfo("Hva som venter", "Verktøy, ideer og framtiden", "Diskuter teknologi og hvordan den forandrer hverdagen."),
        "travelstories" to LocalizedThemeInfo("Et annet sted", "Et sted du husker", "Del reiseopplevelser og drømmereisemål. Sammenlign inntrykk."),
        "restaurant" to LocalizedThemeInfo("Et bord for to", "Spar plass til dessert", "Spill et restaurantbesøk. Øv på bestillinger, ønsker og høflige spørsmål."),
        "neighbours" to LocalizedThemeInfo("Naboskap", "Et kjent fjes", "Prat som naboer. Snakk om nabolaget og små hverdagslige tjenester."),
        "traditions" to LocalizedThemeInfo("Hverdagens skikker", "Små vaner, store fortellinger", "Utforsk tradisjoner og skikker med respekt for variasjoner."),
        "opinions" to LocalizedThemeInfo("Hva mener du?", "Rom for et annet syn", "Velg et hverdagsdilemma. Diskuter begrunnelser og se saken fra flere sider."),
        "future" to LocalizedThemeInfo("Om et år", "Planer verdt å snakke om", "Snakk om håp og framtidsplaner. Se på muligheter og praktiske steg videre."),
        "today" to LocalizedThemeInfo("Verden i dag", "Noe å snakke om", "Spør om aktuelle temaer som interesserer, og diskuter sakens fakta.")
    )

    private val quechua = mapOf(
        "coffee" to LocalizedThemeInfo("¿Aqha icha kaphichu?", "Q'uñi upyana, ama hina kaychu", "Q'uñi wasipi llamk'anki. Yanapay mañakunanpaq hinaspa sumaqta rimanakuychik."),
        "weekend" to LocalizedThemeInfo("Samay p'unchawkuna", "Willariway imaynam karqan", "Samay p'unchawkunamanta tapuy. Ñawpaq ruwasqakunamanta rimaychik."),
        "walk" to LocalizedThemeInfo("Chhikallan puriy", "Chuya wayraman lluqsiy", "Sach'a-sach'api kuska puriyta yuyaykuychik. Sallqa pachamanta, wayramanta, kawsaymanta rimaychik."),
        "dinner" to LocalizedThemeInfo("Ch'isi mikhuy", "Imallatapas wayk'usun", "Ch'isi mikhuyta kuska wakichiychik. Mikhuykunamanta, munasqankumanta, wayk'uymanta tapuy."),
        "introductions" to LocalizedThemeInfo("Kusisqam riqsiyki", "Huch'uymanta qallarisun", "Ñawpaq kutita riqsinakuychik. Sumaqllata tapukuspa imakuna munasqanta yachay."),
        "groceries" to LocalizedThemeInfo("Qhatupi", "Allin rurukunata maskhay", "Qhatupi rantiyta yanapay. Yupaykunata, chaninkunata, tapukuykunata yachaqaychik."),
        "travel" to LocalizedThemeInfo("Qhipa sayana", "Huk llaqtaman purina", "Trenpi icha karupi puriyta wakichiychik. Ñankunamanta, boletokunamanta rimaychik."),
        "home" to LocalizedThemeInfo("Kikin wasiyki", "Wasiykipi hina tiyay", "Wasimanta, ukhukunamanta, imayna sumaq kawsaymantapas rimanakuychik."),
        "friends" to LocalizedThemeInfo("Musuq masikuna", "Huk waqyay ichapas", "Sumaq musuq masi kanki. Kuska imallatapas ruwanapaq rimanakuychik."),
        "work" to LocalizedThemeInfo("Killachaw tutamanta", "Llamk'ana wasipi", "Llamk'aq masi hina rimanakuychik. Llamk'aymanta, huñunakuymanta rimaychik."),
        "weather" to LocalizedThemeInfo("¿Yapamantachu parashan?", "Imayna pacha kaptinpas", "Pachamanta, p'achamanta, hawapi ruwanakunamanta rimanakuychik."),
        "cabin" to LocalizedThemeInfo("Ayllu llaqtaman puriy", "Thak p'unchaw", "Hawa llaqtaman samariq puriyta wakichiychik: puriy, mikhuy, samay."),
        "music" to LocalizedThemeInfo("Kutimun-kutimun taki", "¿Imatataq uyarishanki?", "Munasqan takikunamanta tapuy. Sunqu kuyuykunamanta, takiqkunamanta rimaychik."),
        "film" to LocalizedThemeInfo("Huk t'aqallatawan", "Qhawana allin rikch'ay", "Pelikulakunamanta, seriekunamanta rimanakuychik. Yuyaychakuyta mañay."),
        "books" to LocalizedThemeInfo("Qillqa p'anqakuna ukhupi", "Sunqupi qhipaq willakuy", "Liwrukunamanta, willakuykunamanta, imarayku chaniyuq kasqanmanta rimanakuychik."),
        "design" to LocalizedThemeInfo("Sumaq ruwasqakuna", "Munakuywan ruwasqa", "Wasikunamanta, awasqakunamanta, sumaq ruwasqakunamanta rimanakuychik."),
        "technology" to LocalizedThemeInfo("Qhipaman hamuq", "Yuyaykuna, llamk'anakuna, paqarin", "Musuq kamaykunamanta, imaynam sapa p'unchaw kawsayta tikrasqanmanta rimaychik."),
        "travelstories" to LocalizedThemeInfo("Huk karu llaqta", "Yuyarisqayki k'iti", "Purisqaykichikmanta, musqusqa llaqtakunamanta willanakuychik."),
        "restaurant" to LocalizedThemeInfo("Iskaypaq hamp'ara", "Misk'ipaq qhipakuy", "Mikhuna wasipi hina rimanakuychik. Mañakuykunata, munasqakunata yachaqaychik."),
        "neighbours" to LocalizedThemeInfo("Wasi masi", "Riqsisqa uya", "Wasi masi hina rimanakuychik. Ayllumanta, yanapanakuymanta rimaychik."),
        "traditions" to LocalizedThemeInfo("Ayllu kawsay", "Huch'uy ruwaykuna, hatun willakuykuna", "Sapa p'unchaw kawsaymanta, raymikunamanta, aynimanta yupaychaywan rimaychik."),
        "opinions" to LocalizedThemeInfo("¿Ima ninkitaq?", "Huk yuyaypaq k'iti", "Sapa p'unchaw tapukuyta akllay. Imarayku kasqanta uyariy hinaspa huk yuyaytapas qhaway."),
        "future" to LocalizedThemeInfo("Huk watamanta", "Rimana allin yuyaykuna", "Suyakuykunamanta, hamuq pacha ruwanakunamanta rimanakuychik."),
        "today" to LocalizedThemeInfo("Kunan pacha", "Imamantapas rimarinapaq", "Kunan pacha imachus munasqanmanta tapuy hinaspa chaymanta rimanakuychik.")
    )

    private val english = Themes.shared.associate {
        it.id to LocalizedThemeInfo(it.title, it.subtitle, it.situation)
    }

    private val catalog = mapOf(
        "es" to spanish,
        "en" to english,
        "ko" to korean,
        "ja" to japanese,
        "hi" to hindi,
        "pt" to portuguese,
        "fr" to french,
        "de" to german,
        "it" to italian,
        "zh" to chinese,
        "nb" to norwegian,
        "qu" to quechua
    )

    fun get(theme: ConversationTheme, appLanguageId: String): ConversationTheme {
        val lang = appLanguageId.lowercase()
        val info = catalog[lang]?.get(theme.id)
        return if (info != null) {
            theme.copy(title = info.title, subtitle = info.subtitle, situation = info.situation)
        } else {
            theme
        }
    }
}

fun ConversationTheme.localized(appLanguageId: String): ConversationTheme =
    LocalizedThemes.get(this, appLanguageId)

