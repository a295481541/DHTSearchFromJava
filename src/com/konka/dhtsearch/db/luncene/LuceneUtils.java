package com.konka.dhtsearch.db.luncene;

import java.io.File;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.wltea.analyzer.lucene.IKAnalyzer;

import com.konka.dhtsearch.db.models.DhtInfo_MongoDbPojo;
import com.konka.dhtsearch.db.mongodb.MongodbUtil;
import com.konka.dhtsearch.db.mongodb.MongodbUtilProvider;
import com.konka.dhtsearch.parser.TorrentInfo;
import com.konka.dhtsearch.util.FilterUtil;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.util.JSON;

public class LuceneUtils {
	/**
	 * lucene保存的路径
	 */
	public static final String LUCENE_FILEPATH = "D://lucene.index";
	// 分词的字段
	public static final String KEYWORD = "Keyword";
	// -------------不分词的字段
	public static final String INFO_HASH_FIELD = "info_hash";
	// 数据字段
	public static final String TORRENTINFO_FIELD = "torrentInfo";

	public static final int PAGE_COUNT = 10;// 每页显示的数

	public static final int HITSPERPAGE_COUNT = 20000;// 查询结构总数
//	private static final String NAME_FIELD = "name";
	static int count = 0;

	// DhtInfo_MongoDbPojo
	public static void createIndex() throws Exception {

		MongodbUtil mongodbUtil = getMongodbUtil();
		// DBCursor cursor = mongodbUtil.findDBCursor(DhtInfo_MongoDbPojo.class);
		DBCursor cursor = mongodbUtil.getHaveAnalyticedDhtInfosOfDBCursor();
		Directory index = FSDirectory.open(new File(LUCENE_FILEPATH));
		// TermDocs d
		Analyzer analyzer = new IKAnalyzer();// 这里要换成ik
		// StandardAnalyzer analyzer = new StandardAnalyzer();// 这里要换成ik
		IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_4_9, analyzer);
		IndexWriter indexWriter = new IndexWriter(index, config);
		 indexWriter.deleteAll();
		System.out.println(cursor.count());
		while (cursor.hasNext()) {
			Document doc = new Document();
   
			DBObject object = cursor.next();
			// System.out.println(object);
			DhtInfo_MongoDbPojo dhtInfo_MongoDbPojo = mongodbUtil.loadOne(DhtInfo_MongoDbPojo.class, object);
			TorrentInfo torrentInfo = dhtInfo_MongoDbPojo.getTorrentInfo();
			if (torrentInfo == null || FilterUtil.checkVideoType(torrentInfo)) {// 检测文件类型
				continue;
			}
			System.out.println(object.get(TORRENTINFO_FIELD).toString());
			doc.add(new StringField(INFO_HASH_FIELD, dhtInfo_MongoDbPojo.getInfo_hash(), Field.Store.YES));// StringField不参加分词
//			doc.add(new StoredField(NAME_FIELD, ((BasicDBObject)object.get(TORRENTINFO_FIELD)).getString("name","_").toString()));// StringField不参加分词
			doc.add(new StoredField(TORRENTINFO_FIELD, object.get(TORRENTINFO_FIELD).toString()));// StringField不参加分词
			doc.add(new TextField(KEYWORD, torrentInfo.getNeedSegmentationString(), Field.Store.NO));// 多文件的文件名

			// try {
			indexWriter.addDocument(doc);
			count++;
			// } catch (Exception e) {
			// e.printStackTrace();
			// }
			// System.out.println("ok");

		}
		System.out.println("完毕" + count);
		cursor.close();
		indexWriter.close();
	}

	public static MongodbUtil getMongodbUtil() throws UnknownHostException {
		// Mongo mongoClient = new Mongo();
		// DB db = mongoClient.getDB("test");
		// MongodbUtil mongodbUtil = new MongodbUtil(db);
		MongodbUtil mongodbUtil = MongodbUtilProvider.getMongodbUtil();
		return mongodbUtil;
	}

	/**
	 * 关键字搜索
	 * 
	 * @param searchString
	 *            搜索的字符串
	 * @param page
	 *            页码
	 * @throws Exception
	 */
	public static LuceneSearchResult search(String searchString, int page) throws Exception {
		LuceneSearchResult luceneSearchInfo = new LuceneSearchResult();
		Directory index = FSDirectory.open(new File(LUCENE_FILEPATH));

		BooleanClause.Occur[] clauses = { BooleanClause.Occur.MUST };
		String[] searchFields = { KEYWORD };
		String[] searchFields11 = { searchString };
		// new QueryParser
		// QueryParser qp = new QueryParser(Version.LUCENE_40, fieldName, analyzer);
		// Query qp = new QueryParser(Version.LUCENE_34, fieldName, analyzer);
		// QueryParser d；
		// Query query = MultiFieldQueryParser.parse(Version.LUCENE_4_9, searchString, searchFields, clauses, new IKAnalyzer());
		// IKAnalyzer.
		QueryParser qp = new QueryParser(Version.LUCENE_4_9, KEYWORD, new IKAnalyzer());
		// qp.createBooleanQuery(field, queryText)
		// new QueryParser
		Query query = qp.createBooleanQuery(KEYWORD, searchString, BooleanClause.Occur.MUST);
		// Query query =qp.parse(searchString);
		// FuzzyLikeThisQuery d;
		IndexReader reader = DirectoryReader.open(index);
		IndexSearcher searcher = new IndexSearcher(reader);
		// TopScoreDocCollector.
		// OutOfOrderOneComparatorScoringMaxScoreCollector.
		TopScoreDocCollector collector = TopScoreDocCollector.create(HITSPERPAGE_COUNT, true);
		searcher.search(query, collector);
		// searcher.s
		luceneSearchInfo.setTotal(collector.getTotalHits());
		// System.out.println("总数="+);
		// StringField: 基础文本字段，可指定是否索引
		// StoredField: 仅存储不索引（也就是不能搜索、查询只能跟着文档取出来看）
		// TextField : 会在这上面应用分词器，用来做全文检索的
		ScoreDoc[] hits = collector.topDocs((page - 1) * PAGE_COUNT, PAGE_COUNT).scoreDocs; // 进行分页过滤
		// 4. display results
		System.out.println("Found " + hits.length + " hits.");

		List<DhtInfo_MongoDbPojo> dhtInfo_MongoDbPojos = new ArrayList<DhtInfo_MongoDbPojo>();
		for (int i = 0; i < hits.length; ++i) {
			Document document = searcher.doc(hits[i].doc);
//			System.out.println(document.get(TORRENTINFO_FIELD));
//			System.out.println(document.get(INFO_HASH_FIELD));
//			String ss = toHighlighter(query, document, TORRENTINFO_FIELD);
//			System.out.println(ss);
//			DBObject object = (DBObject) JSON.parse(ss);
			DBObject object = (DBObject) JSON.parse(document.get(TORRENTINFO_FIELD));
			System.out.println(object);
			if (object == null)
				continue;

			TorrentInfo torrentInfo = getMongodbUtil().loadOne(TorrentInfo.class, object);
			DhtInfo_MongoDbPojo dhtInfo_MongoDbPojo = new DhtInfo_MongoDbPojo();

			dhtInfo_MongoDbPojo.setInfo_hash(document.get(INFO_HASH_FIELD));
//			System.out.println(document.get(NAME_FIELD));
//			String name=toHighlighter(query, document, NAME_FIELD);
//			torrentInfo.setName(name);
			dhtInfo_MongoDbPojo.setTorrentInfo(torrentInfo);

			dhtInfo_MongoDbPojos.add(dhtInfo_MongoDbPojo);
			// System.out.println(object);
		}
		reader.close();
		luceneSearchInfo.setLists(dhtInfo_MongoDbPojos);
		return luceneSearchInfo;
	}

	public static DhtInfo_MongoDbPojo searchByInfoHash(String info_hash) throws Exception {
		Directory index = FSDirectory.open(new File(LUCENE_FILEPATH));

		BooleanClause.Occur[] clauses = { BooleanClause.Occur.SHOULD };
		String[] searchFields = { INFO_HASH_FIELD };
		Query query = MultiFieldQueryParser.parse(Version.LUCENE_4_9, info_hash, searchFields, clauses, new IKAnalyzer());
		IndexReader reader = DirectoryReader.open(index);
		IndexSearcher searcher = new IndexSearcher(reader);

		TopScoreDocCollector collector = TopScoreDocCollector.create(1, true);// 只需要查找一个
		searcher.search(query, collector);

		ScoreDoc[] hits = collector.topDocs().scoreDocs; // 进行分页过滤
		// 4. display results
		System.out.println("Found " + hits.length + " hits.");

		List<DhtInfo_MongoDbPojo> dhtInfo_MongoDbPojos = new ArrayList<DhtInfo_MongoDbPojo>();
		for (int i = 0; i < hits.length; ++i) {
			Document document = searcher.doc(hits[i].doc);
			// System.out.println(document.get(TORRENTINFO_FIELD));
			// String ss=toHighlighter(query, document, INFO_HASH_FIELD);
			// System.out.println(ss);
			System.out.println(document.get(INFO_HASH_FIELD));
			DBObject object = (DBObject) JSON.parse(document.get(TORRENTINFO_FIELD));
			System.out.println(object);
			if (object == null)
				continue;

			TorrentInfo torrentInfo = getMongodbUtil().loadOne(TorrentInfo.class, object);
			DhtInfo_MongoDbPojo dhtInfo_MongoDbPojo = new DhtInfo_MongoDbPojo();
			dhtInfo_MongoDbPojo.setInfo_hash(document.get(INFO_HASH_FIELD));
			dhtInfo_MongoDbPojo.setTorrentInfo(torrentInfo);
			dhtInfo_MongoDbPojos.add(dhtInfo_MongoDbPojo);

			return dhtInfo_MongoDbPojo;
		}
		reader.close();
		return null;
	}

//	private static String toHighlighter(Query query, Document doc, String field) {
//		try {
//			SimpleHTMLFormatter simpleHtmlFormatter = new SimpleHTMLFormatter("_","_");
////			SimpleHTMLFormatter simpleHtmlFormatter = new SimpleHTMLFormatter("<font color\\=\"red\">", "</font>");
//			Highlighter highlighter = new Highlighter(simpleHtmlFormatter, new QueryScorer(query));
////			System.out.println("doc.get(NAME_FIELD)="+doc.get(NAME_FIELD));
////			TokenStream tokenStream1 = new IKAnalyzer().tokenStream(NAME_FIELD, new StringReader(doc.get(NAME_FIELD)));
////			String highlighterStr = highlighter.getBestFragment(tokenStream1, doc.get(field));
////			return highlighterStr == null ? doc.get(field) : highlighterStr;
//		} catch ( Exception e) {
//			// TODO Auto-generated catch block
//			// logger.error(e.getMessage());
//		}  
//		return null;
//	}

	public static void main(String[] args) throws Exception {
		args = new String[] { "index", "com_konka_dhtsearch_db_models_DhtInfo_MongoDbPojo", "fileName" };
		args = new String[] { "dd" };
		if (args[0].equals("index")) {
			createIndex();
		} else {
			// searchByInfoHash("c43c0f2569560cc3e135dfba2c8fef106361c2be");
			search(args[0], 1);
		}
		// String str = "中华人民币汇改";
		// List<String> lists = Util.getWords(str, new StandardAnalyzer(luceneVersion));
		// for (String s : lists) {
		// System.out.println(s);
		// }
	}
}
