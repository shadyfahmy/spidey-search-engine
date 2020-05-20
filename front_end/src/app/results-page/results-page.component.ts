import { Component, OnInit } from '@angular/core';
import { FormControl } from '@angular/forms';
import { Observable } from 'rxjs';
import { startWith, map } from 'rxjs/operators';
import { Result } from '../common/result';
import { Routes, RouterModule, Router, ActivatedRoute } from '@angular/router';
import { ResultsService } from '../services/results.service';
import {
  RxSpeechRecognitionService,
  resultList,
} from '@kamiazya/ngx-speech-recognition';
import { AppComponent } from '../app.component';

@Component({
  selector: 'app-results-page',
  templateUrl: './results-page.component.html',
  providers: [RxSpeechRecognitionService],
  styleUrls: ['./results-page.component.scss']
})
export class ResultsPageComponent implements OnInit {
  value = ""
  control = new FormControl();
  suggestions: any[];
  suggestionsTxt: string[] = [];
  results: Result[];
  night = false;
  images: string[];
  listening = false;
  loading = true;
  imageSearch = false;
  recognition = new SpeechRecognition

  constructor(private resultsService: ResultsService,
      public service: RxSpeechRecognitionService,
      private route: ActivatedRoute,
      private router: Router) {

      this.route.params.subscribe( params => {this.value = params.query;
      if(params.im == "true")
        this.imageSearch = true;
      else
        this.imageSearch = false;
    } )  

    this.images = [
  "https://www.gamasutra.com/db_area/images/news/2018/Jun/320213/supermario64thumb1.jpg",
  "https://www.gamasutra.com/db_area/images/news/2018/Jun/320213/supermario64thumb1.jpg",
  "https://www.gamasutra.com/db_area/images/news/2018/Jun/320213/supermario64thumb1.jpg",
  "https://www.gamasutra.com/db_area/images/news/2018/Jun/320213/supermario64thumb1.jpg",
  "https://www.gamasutra.com/db_area/images/news/2018/Jun/320213/supermario64thumb1.jpg",
  "https://www.gamasutra.com/db_area/images/news/2018/Jun/320213/supermario64thumb1.jpg",
  "https://www.gamasutra.com/db_area/images/news/2018/Jun/320213/supermario64thumb1.jpg",
  "https://www.gamasutra.com/db_area/images/news/2018/Jun/320213/supermario64thumb1.jpg",
  "https://www.gamasutra.com/db_area/images/news/2018/Jun/320213/supermario64thumb1.jpg",
  "https://www.gamasutra.com/db_area/images/news/2018/Jun/320213/supermario64thumb1.jpg",
  "https://www.gamasutra.com/db_area/images/news/2018/Jun/320213/supermario64thumb1.jpg"
  ]
  }

  ngOnInit() {
    this.loading = true;

    let user = localStorage.getItem('id')
    if (user != null)
      console.log("data from local storage = " + user)
    else{
      localStorage.setItem('id', "1")
      console.log("data saved to local storage = " +  localStorage.getItem('id'))
    }

    let nm = localStorage.getItem('nightMode')
    if (nm != null) {
      console.log("data from local storage = " + nm)
      if(nm == "true")
        this.night=true;
      else
        this.night=false;
    }
    else{
      localStorage.setItem('nightMode', "false")
      console.log("data saved to local storage = " +  localStorage.getItem('nightMode'))
    }

    if (typeof SpeechRecognition === "undefined") {
      console.log("error")
    } else {
      this.recognition.continuous = true;
      this.recognition.interimResults = true;
      this.recognition.addEventListener("result", this.onResult);
    }

    this.suggestions = null;

    this.resultsService.getResults().subscribe(data => {
      this.results = null
      if(data){
        console.log(data.page);
        this.results = data._embedded.results;
        console.log(this.results)
        this.loading = false
      }      
    })
  }

  Search() {
    if(this.value.replace(/\s/g, '') != ""){
      this.imageSearch = false;
      this.resultsService.saveQuery(this.value).subscribe(data => {
      });
      this.router.navigate(['search', this.value, "false"])
    }
  }

  ImageSearch() {
    if(this.value.replace(/\s/g, '') != ""){
      this.imageSearch = true;
      this.resultsService.saveQuery(this.value).subscribe(data => {
      });
      this.router.navigate(['search', this.value, "true"])
    }
  }

  Suggestions(text : string) {
    this.resultsService.getSuggestions(text).subscribe(data => {
      this.suggestions = data._embedded.queries;
      this.suggestionsTxt = []
      for(let s of this.suggestions) {
        this.suggestionsTxt.push(s.text)
      }
    })
  }

  listen = () => {
    if(!this.listening) {
      console.log("start listening")
      this.listening = true;
      this.recognition.start();
    }
    else{
      console.log("stop listening")
      this.listening = false;
      this.recognition.stop();
    }
  };

  onResult = event => {
    let x =""
    for (const res of event.results) {
      const text = document.createTextNode(res[0].transcript);

      x = x + text.textContent;
    }
    console.log(x);
    this.value = x;
  };

  nightMode() {
    this.night = !this.night;
    if(this.night)
      localStorage.setItem('nightMode', "true")
    else
      localStorage.setItem('nightMode', "false")
  }

}
