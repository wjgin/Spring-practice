package com.jcpdev.board.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import com.jcpdev.board.model.Board;
import com.jcpdev.board.model.PageDto;
import com.jcpdev.board.service.CommentService;
import com.jcpdev.board.service.FreeboardService;

@Controller
@RequestMapping("/community") // .../board/community 부터는 아래로 매핑
public class FreeboardController {
	private static final Logger logger = LoggerFactory.getLogger(FreeboardController.class);

	// 선언을 인터페이스 타입으로 함
	// impl이 하나일때는 bytype으로 자동기입되만 여러개일 때는 @qualifier 로 구분
	@Autowired
	@Qualifier(value="freeBoardServiceImpl")
	FreeboardService service;

	@Autowired
	CommentService cmtservice;

	// 게시판 리스트 보기(검색기능 포함) // request mapping을 여러 url 요청으로 할 수 있음. value가 배열
	// http://localhost:portNum/board/community 또는
	// http://localhost:portNum/board/community/list 와 매핑
	@RequestMapping(value = { "/", "/list" })
	public String pageList(@RequestParam Map<String, Object> param, Model model) { // String page,String field,String
																					// findText,Model model) {
		logger.info("**freeboard list 출력합니다.");

		int currentPage;// 현재 페이지
		List<Board> list;
		int totalCount;
		int pageSize = 10;
		String page = (String) param.get("page");
		if (page == null || page.trim().length() == 0)
			currentPage = 1;
		else
			currentPage = Integer.parseInt(page); // page파라미터가 숫자로 넘어온경우만 실행.

		// page를 사용자 맘대로 문자 대입하면 NumberFormatExceptrion 발생 -> ExceptionHandler 로 처리합니다.

		PageDto pageDto;
		// 검색 기능사용할 때 검색필드와 검색키워드 뷰에 전달한다.아래 PageDto 객체와 중복되는값.편의상.

		String findText = (String) param.get("findText");
		String field = (String) param.get("field");

		totalCount = service.searchCount(param); // 서비스 메소드 타입 변경예정
		pageDto = new PageDto(currentPage, pageSize, totalCount, field, findText);
		list = service.searchList(pageDto); // 주석처리 예정
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("field", field);
		map.put("findText", findText);
		map.put("page", pageDto); // view에게 전달할 모델객체 설정
		map.put("list", list); // "
		
		model.addAllAttributes(map); // 위에 4개의 put 실행한 map객체를 애트리뷰트에 저장한다.

		return "community/list";
	} // view 이름은? list.jsp

	
	// 페이지 서비스를 서비스로 이동시키는 예제: 테스트용으로만 확인
	@RequestMapping(value="/list2")
	public String list2(@RequestParam Map<String, Object> param,Model model) {
		
		// 비지니스로직 실행 후 파라미터 넘기기
		param = service.searchList2(param);
		
		model.addAllAttributes(param);
		
		return "community/list";
	}

	// 상세보기
	@RequestMapping("/detail")
	public String detail(@RequestParam Map<String, Object> param, Model model
			, HttpServletResponse response
			, @CookieValue(name="read", defaultValue = "abcde") String readIdx) {
		// 읽어올 쿠키 이름은 read쿠기 값이 없다면 기본값 "abcde", 쿠키값을 저장할 변수는 readIdx, default 값이 없으면 처음실행시 쿠키값 없어서 오류
		
		int idx = Integer.parseInt((String) param.get("idx"));
		
		// readCookie값의 예시: abcde/3/67/178
		if(!readIdx.contains(String.valueOf(idx))) {	// idx 정수를 String으로 변환 후 더하기
			// 읽지 않은 경우,
			readIdx += "/" + idx;
			// 조회수 증가
			service.updateReadCnt(idx);
		}
		Cookie cookie = new Cookie("read", readIdx);
		cookie.setMaxAge(30*60);	// 초 단위, 30분
		cookie.setPath("/board");	// path 하위에서 cookie정보다 계속 보임
		response.addCookie(cookie);	// 기존 쿠기 넘겨주기
		// cookie는 javascript에서 접근 가능: document.cookie -> 보안 취약점 
		// => HttpOnly true -> 클라이언트단에서 쓰기안됨 secure속성으로 암호화해서 전송 https 프로토콜 통신으로만 사용
		

		cmtservice.updateCountAll(idx); // 댓글 개수 초기화
		
		model.addAttribute("bean", service.getBoardOne(idx));
		model.addAttribute("page", (String)param.get("page"));
		model.addAttribute("cmtlist", cmtservice.commentList(idx)); // 리스트 생성후 바로 넘기기
		model.addAttribute("cr", "\n");
		model.addAttribute("field", (String)param.get("field"));
		model.addAttribute("findText", (String)param.get("findText"));
		
		
		return "community/detail"; // 이 생략됨.
	}

	// 글쓰기 - view : insert() 메소드
	@RequestMapping(value = "/insert")
	public void insert(int page, Model model) {
		model.addAttribute("page", page);
	} // view이름은 insert

	// 글쓰기 - 저장 : save()메소드 리다이렉트 list로.
	@RequestMapping(value = "/save")
	public String save(@ModelAttribute Board board) {
// @ModelAttribute 생략가능 : form 입력 -> @ModelAttribute -> 컨트롤러 -> @ModelAttribute -> view
		service.insert(board);

		return "redirect:/community/list";
	}

	// 수정 화면 표시
	@RequestMapping(value = "update", method = RequestMethod.GET)
	public void update(@RequestParam Map<String, String> param, Model model) { // @RequestParam Map<String, String>
																				// param
		model.addAttribute("bean", service.getBoardOne(Integer.parseInt(param.get("idx"))));
		model.addAllAttributes(param);
	}

	// 수정내용 저장
	@RequestMapping(value="update", method=RequestMethod.POST)
	public String save2(Board board, String page, String field, String findText, Model model) {
		// board는 생략된 @ModelAttribute detail.jsp의 Board dto를 매핑해서 가져옴

		service.update(board);
		model.addAttribute("idx", board.getIdx());
		model.addAttribute("page", page);
		model.addAttribute("field", field);
		model.addAttribute("findText", findText);
		return "redirect:detail";
	}
	
	// 삭제
	@RequestMapping(value="delete")
	public String delete(int idx, int page, String field, String findText, Model model) {
		
		service.delete(idx);
		
		model.addAttribute("page", page);
		model.addAttribute("field", field);
		model.addAttribute("findText", findText);
		return "redirect:list";
	}
	
	

	@ExceptionHandler(NumberFormatException.class)
	public ModelAndView handleErr(HttpServletRequest request) {
		ModelAndView mav = new ModelAndView();
		mav.addObject("url", request.getRequestURL()); // 애트리뷰트 저장
		mav.setViewName("/error/error");
		return mav;
	}

}
