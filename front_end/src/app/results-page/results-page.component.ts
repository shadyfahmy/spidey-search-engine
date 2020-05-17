import { Component, OnInit } from '@angular/core';
import { FormControl } from '@angular/forms';
import { Observable } from 'rxjs';
import { startWith, map } from 'rxjs/operators';
import { Result } from '../common/result';
import { ResultsService } from '../services/results.service';
import {
  RxSpeechRecognitionService,
  resultList,
} from '@kamiazya/ngx-speech-recognition';

@Component({
  selector: 'app-results-page',
  templateUrl: './results-page.component.html',
  providers: [RxSpeechRecognitionService],
  styleUrls: ['./results-page.component.scss']
})
export class ResultsPageComponent implements OnInit {
  value = ""
  control = new FormControl();
  streets: string[];
  filteredStreets: Observable<string[]>;
  results: Result[];
  night = false;
  images: string[];
  listening = false;
  loading = true;
  imageSearch = false;
  recognition = new SpeechRecognition

  constructor(private resultsService: ResultsService, public service: RxSpeechRecognitionService) { 
    this.images = ["https://images.unsplash.com/photo-1494253109108-2e30c049369b?ixlib=rb-1.2.1&ixid=eyJhcHBfaWQiOjEyMDd9&w=1000&q=80",
  "data:image/jpeg;base64,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",
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
    console.log("data from local storage = " + localStorage.getItem('id'))
    if (typeof SpeechRecognition === "undefined") {
      console.log("error")
    } else {
      this.recognition.continuous = true;
      this.recognition.interimResults = true;
      this.recognition.addEventListener("result", this.onResult);
    }

    this.streets = null;
    this.filteredStreets = this.control.valueChanges.pipe(
      startWith(''),
      map(value => this._filter(value))
    );

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

  private _filter(value: string): string[] {
    const filterValue = this._normalizeValue(value);
    return this.streets.filter(street => this._normalizeValue(street).includes(filterValue));
  }

  private _normalizeValue(value: string): string {
    return value.toLowerCase().replace(/\s/g, '');
  }

  Search() {
    this.imageSearch = false;
    if(this.value != ""){
      this.resultsService.saveQuery(this.value).subscribe(data => {
      });
    }
  }

  ImageSearch() {
    this.imageSearch = true;
    if(this.value != ""){
      this.resultsService.saveQuery(this.value).subscribe(data => {
      });
    }
  }

  Suggestions(text : string) {
    this.resultsService.getSuggestions(text).subscribe(data => {
      this.streets = data._embedded.queries;
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

}
